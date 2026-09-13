import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, symlinkSync, unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve, sep } from 'node:path';
import { validateRepository } from './dependency-security-contract.mjs';

const sha = '1'.repeat(40);
const tempBase = resolve(tmpdir());
const fixturePrefix = 'lcg-dependency-contract-';

function removeFixture(root, prefix = fixturePrefix) {
  const resolvedRoot = resolve(root);
  assert.ok(resolvedRoot.startsWith(`${tempBase}${sep}`), `Refusing to clean outside ${tempBase}: ${resolvedRoot}`);
  assert.ok(basename(resolvedRoot).startsWith(prefix), `Refusing to clean unexpected fixture: ${resolvedRoot}`);
  rmSync(resolvedRoot, { recursive: true, force: true });
}

function baseFiles() {
  return {
    '.github/dependabot.yml': `
updates:
  - directory: /renderer
    schedule: { interval: weekly }
    package-ecosystem: npm
  - schedule: { interval: weekly }
    directory: /
    package-ecosystem: gradle
  - package-ecosystem: github-actions
    schedule: { interval: weekly }
    directory: /
  - directory: /tools
    package-ecosystem: pip
    schedule: { interval: monthly }
version: 2
`,
    '.github/workflows/ci.yml': `
name: Renamed normal checks
permissions: { contents: read }
jobs:
  inspect:
    if: needs.scope.outputs.audit == 'true'
    steps:
      - working-directory: renderer
        name: Any display name
        shell: pwsh
        run: |
          npm run audit:security
          if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
      - uses: ./.github/actions/report
  local-call:
    uses: ./.github/workflows/reusable.yml
`,
    '.github/workflows/release.yml': `
name: Renamed candidate checks
permissions: {}
jobs:
  candidate:
    steps:
      - shell: pwsh
        working-directory: renderer
        run: |
          npm.cmd run audit:security
          if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
`,
    '.github/workflows/dependency-security.yml': `
name: Renamed dependency checks
permissions:
  contents: read
jobs:
  review:
    steps:
      - with:
          fail-on-scopes: unknown, runtime, development
          fail-on-severity: moderate
        name: Review dependencies
        uses: actions/dependency-review-action@${sha}
  diagnostics:
    steps:
      - name: Diagnostic only; no production-signing or gh release
        uses: actions/upload-artifact@${sha}
        with:
          path: reports/dependency-security.json
  submit:
    permissions:
      contents: write
    steps:
      - with:
          dependency-graph-continue-on-failure: false
        uses: gradle/actions/dependency-submission@${sha}
`,
    '.github/workflows/reusable.yml': `
name: Local reusable workflow
on: workflow_call
permissions: {}
jobs:
  noop:
    runs-on: ubuntu-latest
    steps:
      - run: echo local
`,
    '.github/actions/report/action.yml': `
name: Local report action
runs:
  using: composite
  steps:
    - uses: actions/cache@${sha}
`,
    'renderer/package.json': JSON.stringify({ scripts: { 'audit:security': 'node ./scripts/audit-security.mjs' } }),
  };
}

function withFixture(t, mutate = () => {}) {
  const root = mkdtempSync(join(tempBase, fixturePrefix));
  t.after(() => removeFixture(root));
  const files = baseFiles();
  mutate(files);
  for (const [path, contents] of Object.entries(files)) {
    const target = join(root, path);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, contents.trimStart());
  }
  return root;
}

function rejects(t, mutate, pattern) {
  assert.throws(() => validateRepository(withFixture(t, mutate)), pattern);
}

test('accepts semantic rewrites, stricter review, npm variants, local actions and diagnostic upload', t => {
  assert.doesNotThrow(() => validateRepository(withFixture(t)));
});

test('accepts equivalent multiline and direct PowerShell exit propagation', t => {
  assert.doesNotThrow(() => validateRepository(withFixture(t, files => {
    files['.github/workflows/ci.yml'] = files['.github/workflows/ci.yml']
      .replace('working-directory: renderer', 'working-directory: .')
      .replace('npm run audit:security', 'npm -C renderer run audit:security')
      .replace('if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }',
        'if ($LASTEXITCODE) {\n            exit $LASTEXITCODE\n          }');
    files['.github/workflows/release.yml'] = files['.github/workflows/release.yml']
      .replace('npm.cmd run audit:security', 'node.exe scripts/audit-security.mjs')
      .replace('if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }', 'exit $LASTEXITCODE');
  })));
});

test('rejects removal of either required audit path', async t => {
  await t.test('normal CI', child => rejects(child, files => {
    files['.github/workflows/ci.yml'] = files['.github/workflows/ci.yml'].replace('npm run audit:security', 'npm run test');
  }, /Android Quality Gate must execute/));
  await t.test('production candidate', child => rejects(child, files => {
    files['.github/workflows/release.yml'] = files['.github/workflows/release.yml'].replace('npm.cmd run audit:security', 'npm.cmd run test');
  }, /Production Release Candidate must execute/));
  await t.test('prefix outside renderer', child => rejects(child, files => {
    files['.github/workflows/release.yml'] = files['.github/workflows/release.yml']
      .replace('working-directory: renderer', 'working-directory: .')
      .replace('npm.cmd run audit:security', 'npm.cmd --prefix ../renderer run audit:security');
  }, /Production Release Candidate must execute/));
});

test('rejects weakened dependency review coverage', async t => {
  await t.test('missing scope', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('unknown, runtime, development', 'runtime, development');
  }, /must cover runtime, development, and unknown/));
  await t.test('critical-only threshold', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('fail-on-severity: moderate', 'fail-on-severity: critical');
  }, /stricter threshold/));
});

test('rejects write, OIDC, environment and secret escalation', async t => {
  await t.test('write outside submission', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('  diagnostics:\n    steps:', '  diagnostics:\n    permissions: { contents: write }\n    steps:');
  }, /diagnostics.*write permissions/));
  await t.test('OIDC on submission', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('      contents: write', '      contents: write\n      id-token: write');
  }, /exactly contents: write/));
  await t.test('arbitrary action in write job', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('    steps:\n      - with:\n          dependency-graph',
        `    steps:\n      - uses: actions/github-script@${sha}\n      - with:\n          dependency-graph`);
  }, /contents: write submission job may run only/));
  await t.test('environment', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('  diagnostics:\n    steps:', '  diagnostics:\n    environment: production-signing\n    steps:');
  }, /must not use an environment/));
  await t.test('secret', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('  diagnostics:\n    steps:', '  diagnostics:\n    env:\n      TOKEN: ${{ secrets.RELEASE_TOKEN }}\n    steps:');
  }, /must not reference environment secrets/));
  await t.test('release publishing action', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace(`actions/upload-artifact@${sha}`, `softprops/action-gh-release@${sha}`);
  }, /must not invoke a release publishing action/));
});

test('rejects unpinned and unsafe action references', async t => {
  await t.test('external tag', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace(`actions/upload-artifact@${sha}`, 'actions/upload-artifact@v4');
  }, /not pinned to a full commit SHA/));
  await t.test('external tag hidden in local action', child => rejects(child, files => {
    files['.github/actions/report/action.yml'] = files['.github/actions/report/action.yml']
      .replace(`actions/cache@${sha}`, 'actions/cache@v4');
  }, /not pinned to a full commit SHA/));
  await t.test('local escape', child => rejects(child, files => {
    files['.github/workflows/ci.yml'] = files['.github/workflows/ci.yml']
      .replace('./.github/actions/report', './../outside');
  }, /escapes the repository/));
  await t.test('missing local action', child => rejects(child, files => {
    files['.github/workflows/ci.yml'] = files['.github/workflows/ci.yml']
      .replace('./.github/actions/report', './.github/actions/missing');
  }, /does not exist/));
  await t.test('local action manifest symlink escape', child => {
    const root = withFixture(child);
    const outsidePrefix = 'lcg-dependency-contract-outside-';
    const outside = mkdtempSync(join(tempBase, outsidePrefix));
    child.after(() => removeFixture(outside, outsidePrefix));
    const externalManifest = join(outside, 'action.yml');
    writeFileSync(externalManifest, baseFiles()['.github/actions/report/action.yml'].trimStart());
    const localManifest = join(root, '.github/actions/report/action.yml');
    unlinkSync(localManifest);
    try {
      symlinkSync(externalManifest, localManifest, 'file');
    } catch (error) {
      if (['EPERM', 'EACCES'].includes(error?.code)) {
        child.skip(`File symlink creation is unavailable: ${error.code}`);
        return;
      }
      throw error;
    }
    assert.throws(() => validateRepository(root), /manifest resolves outside the repository/);
  });
});

test('rejects audit steps disabled by literals or continue-on-error', async t => {
  await t.test('literal false condition', child => rejects(child, files => {
    files['.github/workflows/ci.yml'] = files['.github/workflows/ci.yml']
      .replace('      - working-directory: renderer', '      - if: false\n        working-directory: renderer');
  }, /literal false condition/));
  await t.test('continue on error', child => rejects(child, files => {
    files['.github/workflows/release.yml'] = files['.github/workflows/release.yml']
      .replace('      - shell: pwsh', '      - continue-on-error: true\n        shell: pwsh');
  }, /must not continue on error/));
  await t.test('deleted exit propagation', child => rejects(child, files => {
    files['.github/workflows/release.yml'] = files['.github/workflows/release.yml']
      .replace('          if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }\n', '');
  }, /converted audit exit 1 into success/));
  await t.test('later exit zero', child => rejects(child, files => {
    files['.github/workflows/release.yml'] = files['.github/workflows/release.yml']
      .replace('if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }', 'exit 0');
  }, /converted audit exit 1 into success/));
  await t.test('shell-level success laundering', child => rejects(child, files => {
    files['.github/workflows/release.yml'] = files['.github/workflows/release.yml']
      .replace('npm.cmd run audit:security', 'npm.cmd run audit:security || exit 0');
  }, /Production Release Candidate must execute/));
  await t.test('dependency review continue on error', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('      - with:\n          fail-on-scopes:', '      - continue-on-error: true\n        with:\n          fail-on-scopes:');
  }, /Dependency Review.*must not continue on error/));
  await t.test('dependency submission disabled', child => rejects(child, files => {
    files['.github/workflows/dependency-security.yml'] = files['.github/workflows/dependency-security.yml']
      .replace('      - with:\n          dependency-graph-continue', '      - if: false\n        with:\n          dependency-graph-continue');
  }, /Gradle dependency submission.*literal false/));
});
