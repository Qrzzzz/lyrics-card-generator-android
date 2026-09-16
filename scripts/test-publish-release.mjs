import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { validateAcceptance, validateRun, validateAncestry, validateFiles, validatePublishedAssets, hashFile, acceptanceSummary } from './publish-release.mjs';

const acceptance = () => JSON.parse(readFileSync(new URL('../docs/releases/v1.1.1-acceptance.json', import.meta.url), 'utf8'));
const waivedAcceptance = () => {
  const a = acceptance();
  a.version = '1.1.4';
  a.candidateArtifactName = `production-candidate-${a.version}-${a.sourceCommit.slice(0, 12)}`;
  a.candidate = { versionName: a.version, versionCode: 10104, apkSha256: a.device.installedApkSha256 };
  a.device = null;
  a.confirmedBy = 'Qrzzzz';
  a.manualAcceptanceWaiver = { version: '1.1.4', scope: 'six-manual-checks', authorizedBy: 'Qrzzzz',
    authorization: '默认直接跳过 6 项人工验收，做完后直接发布' };
  for (const check of Object.keys(a.checks)) a.checks[check] = 'NOT RUN';
  return a;
};

test('one-release waiver requires explicit authorization and truthful untested status', () => {
  validateAcceptance(waivedAcceptance(), '1.1.4');
  for (const mutate of [
    a => { delete a.manualAcceptanceWaiver; },
    a => { a.manualAcceptanceWaiver = null; },
    a => { a.manualAcceptanceWaiver.version = '1.1.5'; },
    a => { a.manualAcceptanceWaiver.scope = 'all-validation'; },
    a => { a.manualAcceptanceWaiver.authorizedBy = 'someone-else'; },
    a => { a.manualAcceptanceWaiver.authorization = ''; },
    a => { a.confirmedBy = 'someone-else'; },
    a => { a.checks.open = 'PASS'; },
    a => { a.checks.edit = 'FAIL'; },
    a => { delete a.checks.preview; },
    a => { a.device = acceptance().device; },
    a => { a.candidate.apkSha256 = ''; },
    a => { a.candidate.versionName = '1.1.3'; },
    a => { a.candidate.versionCode = 0; },
    a => { a.sourceCommit = ''; },
    a => { a.candidateRunId = 0; },
    a => { a.candidateRunAttempt = 0; },
    a => { a.candidateArtifactName = '../candidate'; },
    a => { a.dependencyRunId = 0; },
  ]) {
    const a = waivedAcceptance(); mutate(a);
    assert.throws(() => validateAcceptance(a, '1.1.4'));
  }
  const next = waivedAcceptance();
  next.version = next.candidate.versionName = '1.1.5';
  next.candidateArtifactName = `production-candidate-1.1.5-${next.sourceCommit.slice(0, 12)}`;
  assert.throws(() => validateAcceptance(next, '1.1.5'));
  assert.match(acceptanceSummary(waivedAcceptance()), /NOT RUN/);
  assert.doesNotMatch(acceptanceSummary(waivedAcceptance()), /分享面板通过/);
  assert.match(acceptanceSummary(acceptance()), /分享面板通过/);
});

test('optional manual acceptance supports later releases while preserving evidence boundaries', () => {
  for (const version of ['1.1.5', '1.1.6', '1.2.0', '2.0', '2.1']) {
    const a = waivedAcceptance();
    a.version = a.candidate.versionName = a.manualAcceptanceWaiver.version = version;
    a.candidateArtifactName = `production-candidate-${version}-${a.sourceCommit.slice(0, 12)}`;
    a.manualAcceptanceWaiver.authorization = '删掉以前的强制的真机实测环节，做完后直接release';
    validateAcceptance(a, version);
    for (const mutate of [
      b => { b.checks.open = 'PASS'; },
      b => { b.device = acceptance().device; },
      b => { b.manualAcceptanceWaiver.version = '1.1.4'; },
      b => { b.manualAcceptanceWaiver.authorization = ''; },
      b => { b.candidate.apkSha256 = ''; },
      b => { b.candidateRunId = 0; },
    ]) {
      const invalid = structuredClone(a); mutate(invalid);
      assert.throws(() => validateAcceptance(invalid, version));
    }
  }
  const old = waivedAcceptance();
  old.version = old.candidate.versionName = old.manualAcceptanceWaiver.version = '1.1.3';
  old.candidateArtifactName = `production-candidate-1.1.3-${old.sourceCommit.slice(0, 12)}`;
  assert.throws(() => validateAcceptance(old, '1.1.3'));
});

test('manual acceptance rejects incomplete, failed, wrong-version and unbound records', () => {
  validateAcceptance(acceptance(), '1.1.1');
  for (const mutate of [
    a => { a.checks.saveAndOpen = 'NOT RUN'; },
    a => { a.checks.exportPng = 'FAIL'; },
    a => { delete a.checks.shareSheet; },
    a => { a.version = '1.1.2'; },
    a => { a.device.versionName = '1.1.0'; },
    a => { a.device.kind = 'emulator'; },
    a => { a.device.installedApkSha256 = ''; },
    a => { a.candidateRunAttempt = 0; },
    a => { a.candidateArtifactName = '../candidate'; },
    a => { a.notCovered = []; },
    a => { a.confirmedAt = '2999-01-01T00:00:00Z'; },
  ]) {
    const a = acceptance(); mutate(a);
    assert.throws(() => validateAcceptance(a, '1.1.1'));
  }
});

test('run identity and ancestry reject wrong source, PR, failed and unrelated candidates', () => {
  const expected = { repository: 'owner/repo', commit: '1'.repeat(40), path: '.github/workflows/release.yml', event: 'workflow_dispatch', id: 10, attempt: 1 };
  const run = { id: 10, run_attempt: 1, head_repository: { full_name: 'owner/repo' }, head_sha: expected.commit,
    head_branch: 'main', path: expected.path, event: expected.event, status: 'completed', conclusion: 'success' };
  validateRun(run, expected);
  for (const patch of [{ id: 11 }, { run_attempt: 2 }, { event: 'pull_request' }, { head_branch: 'feature' },
    { conclusion: 'failure' }, { head_sha: '2'.repeat(40) }, { path: '.github/workflows/other.yml' },
    { head_repository: { full_name: 'other/repo' } }]) assert.throws(() => validateRun({ ...run, ...patch }, expected));
  validateAncestry({ status: 'ahead', merge_base_commit: { sha: expected.commit } }, expected.commit);
  assert.throws(() => validateAncestry({ status: 'ahead', merge_base_commit: { sha: '2'.repeat(40) } }, expected.commit));
  assert.throws(() => validateAncestry({ status: 'diverged', merge_base_commit: { sha: expected.commit } }, expected.commit));
});

for (const waived of [false, true]) test(`original asset validation rejects corruption and substitution (waiver=${waived})`, async () => {
  const root = mkdtempSync(join(tmpdir(), 'lcg-publish-test-'));
  try {
    const a = waived ? waivedAcceptance() : acceptance();
    const repository = 'Qrzzzz/lyrics-card-generator-android';
    const certificate = 'a'.repeat(64);
    const payloads = [`lyrics-card-generator-android-${a.version}.apk`, `lyrics-card-generator-android-${a.version}.aab`, 'mapping.txt'];
    for (const name of payloads) writeFileSync(join(root, name), `fixture:${name}`);
    const binding = waived ? a.candidate : a.device;
    const hashKey = waived ? 'apkSha256' : 'installedApkSha256';
    binding[hashKey] = await hashFile(join(root, payloads[0]));
    const metadata = { schemaVersion: 2, versionName: a.version, versionCode: binding.versionCode,
      package: 'com.qrzzzz.lyricscard', source: { repository, commit: a.sourceCommit, workflowSha: a.sourceCommit,
        ref: 'refs/heads/main', workflowRef: `${repository}/.github/workflows/release.yml@refs/heads/main`,
        runId: a.candidateRunId, runAttempt: a.candidateRunAttempt, qualityGateRunId: 123 },
      signing: { status: 'verified', certificateSha256: certificate },
      readiness: { status: 'PROVISIONAL', deviceGate: 'NOT RUN', finalReady: false },
      artifactDigests: await Promise.all(payloads.map(async name => ({ name, bytes: statSync(join(root, name)).size, sha256: await hashFile(join(root, name)) }))) };
    writeFileSync(join(root, 'release-metadata.json'), JSON.stringify(metadata));
    const lines = await Promise.all([...payloads, 'release-metadata.json'].map(async name => `${await hashFile(join(root, name))}  ${name}`));
    writeFileSync(join(root, 'SHA256SUMS'), lines.join('\n') + '\n');
    const before = readFileSync(join(root, 'release-metadata.json'), 'utf8');
    const files = await validateFiles(root, a, repository, certificate);
    assert.equal(readFileSync(join(root, 'release-metadata.json'), 'utf8'), before, 'Attested metadata must remain unchanged');
    const wrongBinding = structuredClone(a);
    (waived ? wrongBinding.candidate : wrongBinding.device)[hashKey] = 'f'.repeat(64);
    await assert.rejects(validateFiles(root, wrongBinding, repository, certificate));
    await assert.rejects(validateFiles(root, a, repository, 'b'.repeat(64)));
    await assert.rejects(validateFiles(root, { ...a, sourceCommit: 'f'.repeat(40) }, repository, certificate));
    await assert.rejects(validateFiles(root, { ...a, candidateRunId: a.candidateRunId + 1 }, repository, certificate));
    writeFileSync(join(root, 'extra.apk'), 'unexpected');
    await assert.rejects(validateFiles(root, a, repository, certificate));
    rmSync(join(root, 'extra.apk'));
    writeFileSync(join(root, 'mapping.txt'), 'corruption');
    await assert.rejects(validateFiles(root, a, repository, certificate));
    const assets = files.names.map(name => ({ name, state: 'uploaded', digest: `sha256:${files.hashes[name]}` }));
    validatePublishedAssets(assets, files);
    assert.throws(() => validatePublishedAssets(assets.slice(1), files));
    assert.throws(() => validatePublishedAssets(assets.map((asset, i) => i ? asset : { ...asset, digest: 'sha256:wrong' }), files));
  } finally { rmSync(root, { recursive: true, force: true }); }
});
