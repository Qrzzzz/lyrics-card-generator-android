import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { auditSecurity, classifyAudit } from '../renderer/scripts/audit-security.mjs';
import { classifyCiScope, needsUnicodeSmoke, scopeNames, validateCiSummary } from './ci-scope.mjs';

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

test('CI scope routes docs, ordinary product changes, and conservative inputs', () => {
  const none = Object.fromEntries(scopeNames.map(name => [name, false]));
  assert.deepEqual(classifyCiScope('pull_request', ['docs/CI.md', 'README.md']), none);
  assert.deepEqual(classifyCiScope('pull_request', ['renderer/src/Card.tsx']), { ...none, renderer: true });
  assert.deepEqual(classifyCiScope('pull_request', ['app/src/main/java/example/Editor.kt']), { ...none, android: true });
  for (const path of ['renderer/tests/spec.test.ts', 'renderer/fixtures/card.json',
    'renderer/public/fonts/font.otf', 'renderer/golden/cases.json', 'renderer/index.html']) {
    assert.deepEqual(classifyCiScope('pull_request', [path]), { ...none, renderer: true }, path);
  }
  for (const path of ['app/src/main/java/example/App.kt', 'app/src/main/res/values/strings.xml',
    'app/src/production/java/example/Channel.kt', 'app/src/production/res/values/strings.xml',
    'app/src/debug/java/example/Diagnostics.kt', 'app/src/debug/res/values/strings.xml',
    'app/src/test/java/example/AppTest.kt', 'app/src/test/kotlin/example/AppTest.kt']) {
    assert.deepEqual(classifyCiScope('pull_request', [path]), { ...none, android: true }, path);
  }
  assert.deepEqual(classifyCiScope('pull_request', [
    'renderer/src/Card.tsx', 'app/src/test/java/example/EditorTest.kt',
  ]), { ...none, renderer: true, android: true });
  assert.deepEqual(classifyCiScope('pull_request', [
    'app/src/test/java/example/LyricTextCleanerTest.kt',
  ]), { ...none, android: true, unicode: true });

  for (const path of [
    'docs/releases/v1.1.1/focused-manual-acceptance.json',
    'renderer/schema/render-spec-v1.schema.json',
    'renderer/src/transport.ts',
    'app/src/main/java/example/renderer/RendererController.kt',
    'app/src/main/AndroidManifest.xml',
    'app/src/production/AndroidManifest.xml',
    'app/src/main/res/xml/backup_rules.xml',
    'app/src/debug/res/xml/network_security_config.xml',
    'app/src/androidTest/java/example/DeviceTest.kt',
    'app/src/alpha/java/example/AlphaOnly.kt',
    'app/src/release/res/values/release.xml',
    'app/src/testAlpha/java/example/AlphaTest.kt',
    'app/src/testProductionRelease/java/example/ReleaseTest.kt',
    'app/src/main/kotlin/example/UnknownSource.kt',
    'app/src/test/resources/case.json',
    'app/schemas/com.example.Database/1.json',
    'gradle/libs.versions.toml',
    'renderer/package-lock.json',
    'app/proguard-rules.pro',
    'release-signing.properties.example',
    'scripts/ci-scope.mjs',
    'config/production-signing-policy.json',
    '.github/workflows/ci.yml',
    'renderer/public/unreviewed-runtime.js',
    'renderer/custom-build-input.json',
    'app/src/benchmark/java/example/Benchmark.kt',
    'unclassified-input.txt',
  ]) {
    assert.deepEqual(classifyCiScope('pull_request', [path]),
      Object.fromEntries(scopeNames.map(name => [name, true])), path);
  }
  for (const event of ['push', 'workflow_dispatch', 'schedule']) {
    assert.deepEqual(classifyCiScope(event, ['docs/CI.md']),
      Object.fromEntries(scopeNames.map(name => [name, true])), event);
  }
});

test('audit CLI preserves exit classification and writes diagnostics without network access', t => {
  const tempRoot = resolve(tmpdir());
  const fixture = mkdtempSync(join(tempRoot, 'lcg-audit-cli-'));
  t.after(() => {
    assert.ok(resolve(fixture).startsWith(tempRoot + sep));
    assert.ok(basename(fixture).startsWith('lcg-audit-cli-'));
    rmSync(fixture, { recursive: true, force: true });
  });
  const npmFixture = join(fixture, 'npm-fixture.mjs');
  writeFileSync(npmFixture,
    "process.stdout.write(process.env.FIXTURE_AUDIT_REPORT); process.exitCode = Number(process.env.FIXTURE_AUDIT_EXIT);\n");
  const auditCli = fileURLToPath(new URL('../renderer/scripts/audit-security.mjs', import.meta.url));
  const run = (name, report, exit) => {
    const diagnostic = join(fixture, `${name}.log`);
    const cli = spawnSync(process.execPath, [auditCli], { encoding: 'utf8', env: { ...process.env,
      npm_execpath: npmFixture, FIXTURE_AUDIT_REPORT: JSON.stringify(report), FIXTURE_AUDIT_EXIT: String(exit),
      AUDIT_DIAGNOSTIC_PATH: diagnostic,
    } });
    return { cli, diagnostic: readFileSync(diagnostic, 'utf8') };
  };

  const passing = run('pass', emptyReport(), 0);
  assert.equal(passing.cli.status, 0, passing.cli.stderr);
  assert.match(passing.diagnostic, /"auditReportVersion":2/);

  const vulnerableReport = emptyReport();
  vulnerableReport.vulnerabilities.lib = packageDetail('lib', 'high');
  vulnerableReport.metadata.vulnerabilities.high = vulnerableReport.metadata.vulnerabilities.total = 1;
  const vulnerable = run('vulnerable', vulnerableReport, 1);
  assert.equal(vulnerable.cli.status, 1, vulnerable.cli.stderr);
  assert.match(vulnerable.diagnostic, /SECURITY FAIL: high\/critical advisories found/);

  const unavailable = run('unavailable', {}, 1);
  assert.equal(unavailable.cli.status, 2, unavailable.cli.stderr);
  assert.match(unavailable.diagnostic, /AUDIT UNAVAILABLE: invalid report after 1 attempt/);
});

function summaryNeeds(scope, results = {}) {
  const outputs = Object.fromEntries(scopeNames.map(name => [name, String(scope[name])]));
  const resultFor = (output, jobId) => results[jobId] ?? (scope[output] ? 'success' : 'skipped');
  return {
    scope: { result: results.scope ?? 'success', outputs },
    renderer: { result: resultFor('renderer', 'renderer'), outputs: {} },
    android: { result: resultFor('android', 'android'), outputs: {} },
    contracts: { result: resultFor('contracts', 'contracts'), outputs: {} },
    audit: { result: resultFor('audit', 'audit'), outputs: {} },
    'unicode-path-jvm-smoke': { result: results['unicode-path-jvm-smoke'] ?? 'success', outputs: {} },
  };
}

test('required CI summary accepts scoped skips and fails closed on missing or failed results', () => {
  const none = Object.fromEntries(scopeNames.map(name => [name, false]));
  const renderer = { ...none, renderer: true };
  const full = Object.fromEntries(scopeNames.map(name => [name, true]));
  assert.equal(validateCiSummary('pull_request', summaryNeeds(none)), true);
  assert.equal(validateCiSummary('pull_request', summaryNeeds(renderer)), true);
  assert.equal(validateCiSummary('push', summaryNeeds(full)), true);

  assert.throws(() => validateCiSummary('pull_request', summaryNeeds(renderer, { renderer: 'skipped' })),
    /Required CI child 'renderer' did not succeed/);
  assert.throws(() => validateCiSummary('pull_request', summaryNeeds(none, { renderer: 'failure' })),
    /did not complete safely/);
  assert.throws(() => validateCiSummary('pull_request', summaryNeeds(none, { scope: 'cancelled' })),
    /did not complete safely/);
  const missingChild = summaryNeeds(none);
  delete missingChild.audit;
  assert.throws(() => validateCiSummary('pull_request', missingChild), /missing child 'audit'/);
  const missingOutput = summaryNeeds(none);
  delete missingOutput.scope.outputs.contracts;
  assert.throws(() => validateCiSummary('pull_request', missingOutput), /output 'contracts'.*invalid/);
  assert.throws(() => validateCiSummary('pull_request', summaryNeeds({ ...full, audit: false })),
    /Full CI scope must require every child gate/);
  assert.throws(() => validateCiSummary('push', summaryNeeds(renderer)), /must use full scope/);
  assert.throws(() => validateCiSummary('pull_request', summaryNeeds(none, {
    'unicode-path-jvm-smoke': 'skipped',
  })), /Required CI child 'unicode-path-jvm-smoke' did not succeed/);
});

test('PowerShell wrapper preserves a required summary failure', t => {
  const none = Object.fromEntries(scopeNames.map(name => [name, false]));
  const scopeCli = fileURLToPath(new URL('./ci-scope.mjs', import.meta.url));
  const run = needs => spawnSync('pwsh', ['-NoProfile', '-NonInteractive', '-Command',
    '& $env:CI_SCOPE_NODE $env:CI_SCOPE_SCRIPT --summary; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }'], {
    encoding: 'utf8',
    env: { ...process.env, GITHUB_EVENT_NAME: 'pull_request', CI_SCOPE_NODE: process.execPath,
      CI_SCOPE_SCRIPT: scopeCli, CI_NEEDS_JSON: JSON.stringify(needs) },
  });
  const passing = run(summaryNeeds(none));
  if (passing.error?.code === 'ENOENT') {
    t.skip('pwsh is unavailable');
    return;
  }
  assert.equal(passing.status, 0, passing.stderr);
  assert.match(passing.stdout, /CI required summary PASS/);

  const requiredAndroid = { ...none, android: true };
  const failing = run(summaryNeeds(requiredAndroid, { android: 'skipped' }));
  assert.notEqual(failing.status, 0);
  assert.match(failing.stderr, /Required CI child 'android' did not succeed/);
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
  const scopeCli = fileURLToPath(new URL('./ci-scope.mjs', import.meta.url));
  for (const [index, [path, expected]] of [
    ['scripts/中文.ps1', true],
    ['scripts/with\nnewline.ps1', true],
    ['docs/中文说明.md', false],
    ['docs/review\nscripts/ghost.ps1', false],
  ].entries()) {
    const head = git(['commit-tree', treeFor(path.split('/')), '-p', base, '-m', 'path fixture']).trim();
    const outputPath = join(fixture, `output-${index}`);
    const cli = spawnSync(process.execPath, [scopeCli], {
      cwd: fixture, encoding: 'utf8', env: { ...env, GITHUB_EVENT_NAME: 'pull_request',
        PR_BASE_SHA: base, PR_HEAD_SHA: head, GITHUB_OUTPUT: outputPath },
    });
    assert.equal(cli.status, 0, cli.stderr);
    const scope = classifyCiScope('pull_request', [path]);
    assert.equal(scope.unicode, expected, JSON.stringify(path));
    assert.equal(readFileSync(outputPath, 'utf8'),
      scopeNames.map(name => `${name}=${scope[name]}`).join('\n') + '\n', JSON.stringify(path));
  }

  const missingRefOutput = join(fixture, 'missing-ref-output');
  const missingRef = spawnSync(process.execPath, [scopeCli], {
    cwd: fixture, encoding: 'utf8', env: { ...env, GITHUB_EVENT_NAME: 'pull_request',
      PR_BASE_SHA: base, PR_HEAD_SHA: 'f'.repeat(40), GITHUB_OUTPUT: missingRefOutput },
  });
  assert.notEqual(missingRef.status, 0);
  assert.equal(existsSync(missingRefOutput), false, 'A failed Git diff must not emit permissive scope outputs.');

  const malformedSummary = spawnSync(process.execPath, [scopeCli, '--summary'], {
    cwd: fixture, encoding: 'utf8', env: { ...env, GITHUB_EVENT_NAME: 'pull_request', CI_NEEDS_JSON: '{' },
  });
  assert.notEqual(malformedSummary.status, 0, 'Malformed needs JSON must fail the required summary.');
});
