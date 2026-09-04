import { test } from 'node:test';
import assert from 'node:assert/strict';
import { auditSecurity, classifyAudit } from '../renderer/scripts/audit-security.mjs';
import { needsUnicodeSmoke } from './ci-scope.mjs';

const clean = { status: 0, stdout: JSON.stringify({ metadata: { vulnerabilities:
  { info: 0, low: 2, moderate: 1, high: 0, critical: 0, total: 3 } } }) };
const network = { status: 1, stdout: JSON.stringify({ error: { code: 'E503' } }) };
test('only complete successful audits without high/critical findings pass', () => {
  assert.equal(classifyAudit(clean), 'pass');
  assert.equal(classifyAudit({ status: 0, stdout: '{}' }), 'invalid');
  assert.equal(classifyAudit({ status: 0, stdout: 'not json' }), 'invalid');
  assert.equal(classifyAudit({ ...clean, status: 1 }), 'invalid');
  assert.equal(classifyAudit({ status: 1, stdout: 'undefined', stderr: 'npm warn audit network timeout at: https://registry.npmjs.org' }), 'transient');
  assert.equal(classifyAudit({ status: null, error: { code: 'ETIMEDOUT' } }), 'transient');
});
test('transient audit errors retry, exhausted outages remain nonzero', async () => {
  let calls = 0;
  const options = { sleep: async () => {}, log: () => {} };
  assert.equal(await auditSecurity({ ...options, run: () => ++calls < 3 ? network : clean }), 0);
  assert.equal(calls, 3);
  calls = 0;
  assert.equal(await auditSecurity({ ...options, run: () => { calls++; return network; } }), 2);
  assert.equal(calls, 3);
});
test('advisories and malformed/auth failures never retry or pass', async () => {
  for (const result of [
    { ...clean, status: 1, stdout: clean.stdout.replace('"high":0', '"high":1') },
    { status: 1, stdout: JSON.stringify({ error: { code: 'E401' } }) },
    { status: 0, stdout: 'invalid' },
  ]) {
    let calls = 0;
    const exit = await auditSecurity({ run: () => { calls++; return result; }, log: () => {}, sleep: async () => {} });
    assert.notEqual(exit, 0);
    assert.equal(calls, 1);
  }
});
test('Unicode scope preserves build/toolchain regressions and skips unrelated PRs', () => {
  for (const path of ['gradle/libs.versions.toml', 'gradlew.bat', 'app/build.gradle.kts',
    'scripts/ci-scope.mjs', 'renderer/package-lock.json', '.github/workflows/ci.yml']) {
    assert.equal(needsUnicodeSmoke('pull_request', [path]), true, path);
  }
  for (const path of ['README.md', 'docs/RELEASE_READINESS.md', 'renderer/src/Card.tsx',
    'app/src/main/java/com/qrzzzz/lyricscard/ui/Editor.kt']) {
    assert.equal(needsUnicodeSmoke('pull_request', [path]), false, path);
  }
  assert.equal(needsUnicodeSmoke('push'), true);
  assert.equal(needsUnicodeSmoke('workflow_dispatch'), true);
});
