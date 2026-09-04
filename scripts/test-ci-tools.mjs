import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { auditSecurity, classifyAudit } from '../renderer/scripts/audit-security.mjs';
import { needsUnicodeSmoke } from './ci-scope.mjs';

function packageDetail(name, severity) {
  return { name, severity, isDirect: true, range: '<2.0.0', nodes: [`node_modules/${name}`], effects: [],
    via: [{ source: 100001, name, dependency: name, severity, title: 'Fixture advisory',
      url: 'https://example.invalid/advisory', range: '<2.0.0' }], fixAvailable: false };
}
function emptyReport() {
  return { auditReportVersion: 2, vulnerabilities: {}, metadata: {
    vulnerabilities: { info: 0, low: 0, moderate: 0, high: 0, critical: 0, total: 0 },
    // Dependency flags overlap; this total intentionally is not their sum.
    dependencies: { prod: 10, dev: 158, optional: 53, peer: 0, peerOptional: 0, total: 167 },
  } };
}
const result = (report, status = 0) => ({ status, stdout: JSON.stringify(report) });
const clean = result(emptyReport());
const network = result({ error: { code: 'E503' } }, 1);
const quiet = { sleep: async () => {}, log: () => {} };

async function assertTerminal(first, expectedExit) {
  let calls = 0;
  const exit = await auditSecurity({ ...quiet, run: () => ++calls === 1 ? first : clean });
  assert.equal(exit, expectedExit);
  assert.equal(calls, 1, 'A later clean report must not erase a terminal failure.');
}

test('valid v2 reports allow low/moderate findings and overlapping dependency categories', () => {
  assert.equal(classifyAudit(clean), 'pass');
  const report = emptyReport();
  report.vulnerabilities['low-lib'] = packageDetail('low-lib', 'low');
  report.vulnerabilities['moderate-lib'] = packageDetail('moderate-lib', 'moderate');
  report.vulnerabilities['moderate-lib'].via = ['low-lib'];
  report.vulnerabilities['moderate-lib'].fixAvailable = { name: 'moderate-lib', version: '2.0.0', isSemVerMajor: true };
  report.metadata.vulnerabilities = { info: 0, low: 1, moderate: 1, high: 0, critical: 0, total: 2 };
  assert.equal(classifyAudit(result(report)), 'pass');
});

test('complete high and critical reports fail immediately even if npm exits zero', async () => {
  for (const severity of ['high', 'critical']) {
    const report = emptyReport();
    report.vulnerabilities.lib = packageDetail('lib', severity);
    report.metadata.vulnerabilities[severity] = 1;
    report.metadata.vulnerabilities.total = 1;
    assert.equal(classifyAudit(result(report)), 'vulnerable');
    await assertTerminal(result(report), 1);
  }
});

test('inconsistent summary/detail and malformed schemas cannot retry into success', async t => {
  const cases = {
    'total differs from severity sum': r => { r.metadata.vulnerabilities.total = 1; },
    'high detail hidden behind zero summary': r => { r.vulnerabilities.lib = packageDetail('lib', 'high'); },
    'counts without matching packages': r => { r.metadata.vulnerabilities.low = r.metadata.vulnerabilities.total = 1; },
    'missing report version': r => { delete r.auditReportVersion; },
    'unsupported report version': r => { r.auditReportVersion = 3; },
    'array instead of package map': r => { r.vulnerabilities = []; },
    'missing dependency metadata': r => { delete r.metadata.dependencies; },
    'string instead of a severity count': r => { r.metadata.vulnerabilities.high = '0'; },
    'high advisory hidden behind low package severity': r => {
      r.vulnerabilities.lib = packageDetail('lib', 'low');
      r.vulnerabilities.lib.via[0].severity = 'high';
      r.metadata.vulnerabilities.low = r.metadata.vulnerabilities.total = 1;
    },
    'missing advisory fields': r => {
      r.vulnerabilities.lib = packageDetail('lib', 'low');
      delete r.vulnerabilities.lib.via[0].title;
      r.metadata.vulnerabilities.low = r.metadata.vulnerabilities.total = 1;
    },
    'missing metavulnerability target': r => {
      r.vulnerabilities.lib = packageDetail('lib', 'low');
      r.vulnerabilities.lib.via = ['absent'];
      r.metadata.vulnerabilities.low = r.metadata.vulnerabilities.total = 1;
    },
  };
  for (const [name, mutate] of Object.entries(cases)) await t.test(name, async () => {
    const report = emptyReport();
    mutate(report);
    const first = { ...result(report), stderr: 'network timeout' };
    assert.equal(classifyAudit(first), 'invalid');
    await assertTerminal(first, 2);
  });
  await assertTerminal({ status: 1, stdout: 'undefined', stderr: 'network timeout' }, 2);
  await assertTerminal({ ...clean, status: 1 }, 2);
});

test('explicit auth/nontransient errors outrank timeout prose and transient codes', async () => {
  for (const first of [
    result({ error: { code: 'E401', summary: 'network timeout' } }, 1),
    result({ error: { code: 'E403', detail: 'request timed out' } }, 1),
    result({ statusCode: 401, message: 'network timeout', error: { code: 'E503' } }, 1),
    result({ error: { code: 'EACCES', summary: 'network timeout' } }, 1),
    { ...network, error: { code: 'E401' }, stderr: 'network timeout' },
    { status: 1, stdout: '', stderr: 'npm error code E401\nnetwork timeout' },
  ]) {
    assert.equal(classifyAudit(first), 'invalid');
    await assertTerminal(first, 2);
  }
});

test('recognized service/transport failures retry finitely; exhausted outages stay failed', async () => {
  for (const first of [network,
    result({ statusCode: 503, message: 'Service Unavailable', body: '' }, 1),
    { status: null, error: { code: 'ETIMEDOUT' } },
    { status: 1, stdout: '', stderr: 'npm warn audit network timeout at: https://registry.npmjs.org' },
  ]) {
    assert.equal(classifyAudit(first), 'transient');
    let calls = 0;
    assert.equal(await auditSecurity({ ...quiet, run: () => ++calls < 3 ? first : clean }), 0);
    assert.equal(calls, 3);
    calls = 0;
    assert.equal(await auditSecurity({ ...quiet, run: () => { calls++; return first; } }), 2);
    assert.equal(calls, 3);
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

test('real Git diff and CLI preserve Unicode/newline filenames without false scope decisions', t => {
  const tempRoot = resolve(tmpdir());
  const fixture = mkdtempSync(join(tempRoot, 'lcg-ci-scope-'));
  t.after(() => {
    assert.ok(resolve(fixture).startsWith(tempRoot + sep));
    assert.ok(basename(fixture).startsWith('lcg-ci-scope-'));
    rmSync(fixture, { recursive: true, force: true });
  });
  const env = { ...process.env };
  for (const key of ['GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES']) delete env[key];
  const git = (args, input = '') => execFileSync('git', ['-c', 'user.name=CI Fixture',
    '-c', 'user.email=ci@example.invalid', '-c', 'commit.gpgsign=false', ...args],
  { cwd: fixture, env, input, encoding: 'utf8' });
  git(['init', '--quiet']);
  git(['config', 'core.quotePath', 'true']);
  const emptyTree = git(['mktree']).trim();
  const base = git(['commit-tree', emptyTree, '-m', 'base']).trim();
  const blob = git(['hash-object', '-w', '--stdin'], 'fixture\n').trim();
  // Build real Git trees directly: Windows cannot create newline filenames
  // through Win32, while PR trees from other platforms can contain them.
  function treeFor(parts) {
    if (parts.length === 1) return git(['mktree', '-z'], `100644 blob ${blob}\t${parts[0]}\0`).trim();
    const subtree = treeFor(parts.slice(1));
    return git(['mktree', '-z'], `040000 tree ${subtree}\t${parts[0]}\0`).trim();
  }
  for (const [index, [path, expected]] of [
    ['scripts/中文.ps1', true],
    ['scripts/with\nnewline.ps1', true],
    ['docs/中文说明.md', false],
    ['docs/review\nscripts/ghost.ps1', false],
  ].entries()) {
    const head = git(['commit-tree', treeFor(path.split('/')), '-p', base, '-m', 'path fixture']).trim();
    const outputPath = join(fixture, `output-${index}`);
    const cli = spawnSync(process.execPath, [fileURLToPath(new URL('./ci-scope.mjs', import.meta.url))], {
      cwd: fixture, encoding: 'utf8', env: { ...env, GITHUB_EVENT_NAME: 'pull_request',
        PR_BASE_SHA: base, PR_HEAD_SHA: head, GITHUB_OUTPUT: outputPath },
    });
    assert.equal(cli.status, 0, cli.stderr);
    assert.equal(readFileSync(outputPath, 'utf8'), `unicode=${expected}\n`, JSON.stringify(path));
  }
});
