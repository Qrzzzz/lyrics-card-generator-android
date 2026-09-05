import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createReadStream, readFileSync, readdirSync, lstatSync, appendFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';

export const requiredChecks = ['open', 'edit', 'preview', 'exportPng', 'saveAndOpen', 'shareSheet'];
const sha = /^[0-9a-f]{40}$/;
const digest = /^[0-9a-f]{64}$/;
const positive = n => Number.isSafeInteger(n) && n > 0;
const json = path => JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''));
export async function hashFile(path) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(path)) hash.update(chunk);
  return hash.digest('hex');
}

export function validateAcceptance(a, version) {
  assert.match(version, /^\d+\.\d+\.\d+$/);
  assert.equal(a.schemaVersion, 1);
  assert.equal(a.policy, 'focused-manual-v1');
  assert.equal(a.version, version);
  assert.match(a.sourceCommit, sha);
  assert.ok(positive(a.candidateRunId) && positive(a.candidateRunAttempt) && positive(a.dependencyRunId));
  assert.equal(a.candidateArtifactName, `production-candidate-${version}-${a.sourceCommit.slice(0, 12)}`);
  assert.equal(a.device.kind, 'physical');
  assert.ok(typeof a.device.model === 'string' && a.device.model.trim());
  assert.ok(Number.isInteger(a.device.api) && a.device.api >= 26);
  assert.match(a.device.installedApkSha256, digest);
  assert.equal(a.device.versionName, version);
  assert.ok(positive(a.device.versionCode));
  assert.match(a.confirmedBy, /^[A-Za-z0-9-]{1,39}$/);
  assert.ok(Number.isFinite(Date.parse(a.confirmedAt)) && Date.parse(a.confirmedAt) <= Date.now());
  assert.ok(typeof a.confirmation === 'string' && a.confirmation.trim());
  assert.deepEqual(Object.keys(a.checks).sort(), [...requiredChecks].sort());
  for (const check of requiredChecks) assert.equal(a.checks[check], 'PASS', `${check} requires actual confirmation`);
  assert.ok(Array.isArray(a.notCovered) && a.notCovered.length > 0);
  assert.ok(a.notCovered.every(item => typeof item === 'string' && item.trim()));
  return a;
}

export function validateRun(run, { repository, commit, path, event, id, attempt }) {
  assert.equal(run.id, id);
  assert.equal(run.head_repository.full_name, repository);
  assert.equal(run.head_sha, commit);
  assert.equal(run.head_branch, 'main');
  assert.equal(run.path, path);
  assert.equal(run.event, event);
  assert.equal(run.status, 'completed');
  assert.equal(run.conclusion, 'success');
  if (attempt !== undefined) assert.equal(run.run_attempt, attempt);
}

export function validateAncestry(comparison, source) {
  assert.ok(['ahead', 'identical'].includes(comparison.status));
  assert.equal(comparison.merge_base_commit.sha, source);
}

export async function validateFiles(root, a, repository, certificate) {
  const names = [`lyrics-card-generator-android-${a.version}.apk`, `lyrics-card-generator-android-${a.version}.aab`,
    'mapping.txt', 'release-metadata.json', 'SHA256SUMS'].sort();
  assert.deepEqual(readdirSync(root).sort(), names, 'Exactly five original candidate assets are required');
  for (const name of names) assert.ok(lstatSync(join(root, name)).isFile(), 'Assets must be regular files');
  const metadata = json(join(root, 'release-metadata.json'));
  assert.equal(metadata.schemaVersion, 2);
  assert.equal(metadata.versionName, a.version);
  assert.equal(metadata.versionCode, a.device.versionCode);
  assert.equal(metadata.package, 'com.qrzzzz.lyricscard');
  assert.equal(metadata.source.repository, repository);
  assert.equal(metadata.source.commit, a.sourceCommit);
  assert.equal(metadata.source.workflowSha, a.sourceCommit);
  assert.equal(metadata.source.ref, 'refs/heads/main');
  assert.equal(metadata.source.workflowRef, `${repository}/.github/workflows/release.yml@refs/heads/main`);
  assert.equal(metadata.source.runId, a.candidateRunId);
  assert.equal(metadata.source.runAttempt, a.candidateRunAttempt);
  assert.ok(positive(metadata.source.qualityGateRunId));
  assert.equal(metadata.signing.status, 'verified');
  assert.equal(metadata.signing.certificateSha256, certificate);
  // Metadata is the original attested build-time record. Never rewrite its readiness.
  const hashes = Object.fromEntries(await Promise.all(names.map(async name => [name, await hashFile(join(root, name))])));
  assert.equal(hashes[`lyrics-card-generator-android-${a.version}.apk`], a.device.installedApkSha256);
  const payloadNames = names.filter(name => !['release-metadata.json', 'SHA256SUMS'].includes(name));
  assert.deepEqual(metadata.artifactDigests.map(item => item.name).sort(), payloadNames);
  for (const item of metadata.artifactDigests) {
    assert.equal(item.sha256, hashes[item.name]);
    assert.equal(item.bytes, lstatSync(join(root, item.name)).size);
  }
  const checksums = readFileSync(join(root, 'SHA256SUMS'), 'utf8').replace(/^\uFEFF/, '').trim().split(/\r?\n/).map(line => {
    const match = /^([0-9a-f]{64})  ([A-Za-z0-9._-]+)$/.exec(line);
    assert.ok(match, 'Malformed checksum entry');
    return { name: match[2], hash: match[1] };
  });
  assert.deepEqual(checksums.map(item => item.name).sort(), names.filter(name => name !== 'SHA256SUMS'));
  for (const item of checksums) assert.equal(item.hash, hashes[item.name]);
  return { names, hashes, metadata };
}

export function validatePublishedAssets(assets, files) {
  assert.deepEqual(assets.map(asset => asset.name).sort(), files.names);
  for (const asset of assets) {
    assert.equal(asset.state, 'uploaded');
    assert.equal(asset.digest, `sha256:${files.hashes[asset.name]}`);
  }
}

async function api(endpoint, { method = 'GET', body, optional = false } = {}) {
  const response = await fetch(`https://api.github.com/${endpoint}`, {
    method, headers: { Authorization: `Bearer ${process.env.GH_TOKEN}`, Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28', 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(60000),
  });
  if (optional && response.status === 404) return null;
  if (!response.ok) throw new Error(`GitHub ${method} ${endpoint}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}

async function main() {
  const version = process.env.RELEASE_VERSION;
  assert.match(version ?? '', /^\d+\.\d+\.\d+$/);
  assert.equal(process.env.GITHUB_EVENT_NAME, 'workflow_dispatch');
  assert.equal(process.env.GITHUB_REF, 'refs/heads/main');
  const validator = process.env.GITHUB_WORKFLOW_SHA;
  assert.match(validator ?? '', sha);
  assert.equal(process.env.GITHUB_SHA, validator);
  const repository = process.env.GITHUB_REPOSITORY;
  assert.match(repository ?? '', /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/);
  const a = validateAcceptance(json(`docs/releases/v${version}-acceptance.json`), version);
  const policy = json('config/production-signing-policy.json');
  assert.equal(repository, policy.repository);
  if (process.argv[2] === 'prepare') {
    appendFileSync(process.env.GITHUB_OUTPUT, `run_id=${a.candidateRunId}\nartifact_name=${a.candidateArtifactName}\n`);
    return;
  }
  assert.equal(process.argv[2], 'publish');
  const root = resolve('release-input');
  const files = await validateFiles(root, a, repository, policy.certificateSha256);
  const repo = `repos/${repository}`;
  const candidate = await api(`${repo}/actions/runs/${a.candidateRunId}`);
  validateRun(candidate, { repository, commit: a.sourceCommit, path: '.github/workflows/release.yml',
    event: 'workflow_dispatch', id: a.candidateRunId, attempt: a.candidateRunAttempt });
  for (const [id, path] of [[files.metadata.source.qualityGateRunId, '.github/workflows/ci.yml'],
    [a.dependencyRunId, '.github/workflows/dependency-security.yml']]) {
    validateRun(await api(`${repo}/actions/runs/${id}`), { repository, commit: a.sourceCommit, path, event: 'push', id });
  }
  validateAncestry(await api(`${repo}/compare/${a.sourceCommit}...${validator}`), a.sourceCommit);
  const remoteMain = await api(`${repo}/git/ref/heads/main`);
  validateAncestry(await api(`${repo}/compare/${validator}...${remoteMain.object.sha}`), validator);
  for (const name of files.names) {
    execFileSync('gh', ['attestation', 'verify', join(root, name), '--repo', repository,
      '--signer-workflow', `${repository}/.github/workflows/release.yml`, '--source-ref', 'refs/heads/main',
      '--source-digest', a.sourceCommit], { stdio: 'inherit', timeout: 120000 });
  }
  const tag = `v${version}`;
  const ref = await api(`${repo}/git/ref/tags/${tag}`, { optional: true });
  if (ref) {
    assert.equal(ref.object.type, 'tag', 'Existing release tag must be annotated');
    const object = await api(`${repo}/git/tags/${ref.object.sha}`);
    assert.equal(object.tag, tag);
    assert.equal(object.object.type, 'commit');
    assert.equal(object.object.sha, a.sourceCommit, 'Refuse to move an existing tag');
  }
  const release = await api(`${repo}/releases/tags/${tag}`, { optional: true });
  if (release && !release.draft) {
    assert.ok(ref);
    validatePublishedAssets(release.assets, files);
    writeFileSync('publication-result.json', JSON.stringify({ status: 'ALREADY_PUBLISHED', policy: a.policy,
      version, sourceCommit: a.sourceCommit, validatorCommit: validator, releaseUrl: release.html_url,
      assets: files.hashes }, null, 2));
    console.log(`Already published and verified: ${release.html_url}`);
    return;
  }
  const notes = readFileSync(`docs/releases/v${version}.md`, 'utf8');
  const runUrl = `https://github.com/${repository}/actions/runs/${process.env.GITHUB_RUN_ID}`;
  const acceptanceUrl = `https://github.com/${repository}/blob/${validator}/docs/releases/v${version}-acceptance.json`;
  const body = `${notes.trim()}\n\n## 发布验收\n\n` +
    `- 发布策略：focused-manual-v1；[人工验收记录](${acceptanceUrl})，由 ${a.confirmedBy} 于 ${a.confirmedAt} 确认。\n` +
    `- Source / tag：\`${a.sourceCommit}\`；[签名候选](${candidate.html_url})，attempt ${a.candidateRunAttempt}。\n` +
    `- 设备：${a.device.model} / API ${a.device.api}；打开、编辑、预览、PNG 导出、保存后打开和分享面板通过。\n` +
    `- 本次不覆盖：${a.notCovered.join('；')}。\n` +
    `- 生产证书 SHA-256：\`${policy.certificateSha256}\`。\n` +
    `- [发布运行](${runUrl})：复核候选来源、同 SHA CI、全部五个附件的哈希和 GitHub attestation。\n\n` +
    '附件为签名候选原字节。release-metadata.json 的 PROVISIONAL / NOT RUN / finalReady=false 是构建时记录，保持原样；本次采用上述人工验收策略，不宣称旧完整设备矩阵通过。\n';
  const marker = `<!-- focused-manual-v1:${a.sourceCommit} -->`;
  if (release) assert.ok(release.body?.includes(marker), 'Existing draft requires manual review');
  if (!ref) {
    const object = await api(`${repo}/git/tags`, { method: 'POST', body: { tag, message: `${tag}\n\n${runUrl}`,
      object: a.sourceCommit, type: 'commit' } });
    await api(`${repo}/git/refs`, { method: 'POST', body: { ref: `refs/tags/${tag}`, sha: object.sha } });
  }
  const draft = release ?? await api(`${repo}/releases`, { method: 'POST', body: { tag_name: tag,
    target_commitish: a.sourceCommit, name: tag, body: `${marker}\n${body}`, draft: true, prerelease: false } });
  // A partial draft can resume without replacing any previously uploaded bytes.
  for (const asset of draft.assets) {
    assert.ok(files.names.includes(asset.name), 'Unexpected draft asset');
    assert.equal(asset.digest, `sha256:${files.hashes[asset.name]}`, 'Draft asset differs from candidate');
  }
  for (const name of files.names.filter(name => !draft.assets.some(asset => asset.name === name))) {
    execFileSync('gh', ['release', 'upload', tag, join(root, name), '--repo', repository], { stdio: 'inherit', timeout: 300000 });
  }
  const ready = await api(`${repo}/releases/${draft.id}`);
  validatePublishedAssets(ready.assets, files);
  const published = await api(`${repo}/releases/${draft.id}`, { method: 'PATCH',
    body: { body: `${marker}\n${body}`, draft: false, make_latest: 'true' } });
  validatePublishedAssets(published.assets, files);
  writeFileSync('publication-result.json', JSON.stringify({ status: 'PUBLISHED', policy: a.policy, version,
    sourceCommit: a.sourceCommit, validatorCommit: validator, candidateRunId: a.candidateRunId,
    releaseUrl: published.html_url, assets: files.hashes }, null, 2));
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, `Published [${tag}](${published.html_url}) with five verified original assets.\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
