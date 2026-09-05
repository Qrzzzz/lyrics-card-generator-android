import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { validateAcceptance, validateRun, validateAncestry, validateFiles, validatePublishedAssets, hashFile } from './publish-release.mjs';

const acceptance = () => JSON.parse(readFileSync(new URL('../docs/releases/v1.1.1-acceptance.json', import.meta.url), 'utf8'));

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

test('original asset validation detects corruption, substitution, extra files and wrong installation', async () => {
  const root = mkdtempSync(join(tmpdir(), 'lcg-publish-test-'));
  try {
    const a = acceptance();
    const repository = 'Qrzzzz/lyrics-card-generator-android';
    const certificate = 'a'.repeat(64);
    const payloads = ['lyrics-card-generator-android-1.1.1.apk', 'lyrics-card-generator-android-1.1.1.aab', 'mapping.txt'];
    for (const name of payloads) writeFileSync(join(root, name), `fixture:${name}`);
    a.device.installedApkSha256 = await hashFile(join(root, payloads[0]));
    const metadata = { schemaVersion: 2, versionName: a.version, versionCode: a.device.versionCode,
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
    await assert.rejects(validateFiles(root, { ...a, device: { ...a.device, installedApkSha256: 'f'.repeat(64) } }, repository, certificate));
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
