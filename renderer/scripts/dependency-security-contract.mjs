import { existsSync, readFileSync, readdirSync, realpathSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, extname, isAbsolute, posix, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { readYaml } from './read-yaml.mjs';

const externalAction = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\/[A-Za-z0-9_.\/-]+)?@[0-9a-fA-F]{40}$/;
const requiredDependabotDirectories = new Map([
  ['npm', '/renderer'],
  ['gradle', '/'],
  ['github-actions', '/'],
]);
const requiredScopes = new Set(['runtime', 'development', 'unknown']);
const acceptedSeverityThresholds = new Set(['low', 'moderate', 'high']);
const submissionActionPrefixes = [
  'actions/checkout@',
  'actions/setup-java@',
  'actions/upload-artifact@',
  'gradle/actions/dependency-submission@',
];

function fail(message) {
  throw new Error(message);
}

function stepsIn(workflow) {
  return Object.entries(workflow?.jobs ?? {}).flatMap(([jobId, job]) =>
    (Array.isArray(job?.steps) ? job.steps : []).map(step => ({ jobId, job, step })),
  );
}

function isLiteralFalse(value) {
  if (value === false) return true;
  return typeof value === 'string' && /^(?:\$\{\{\s*)?false(?:\s*\}\})?$/i.test(value.trim());
}

function permitsContinueOnError(value) {
  if (value === undefined || value === false) return false;
  return !(typeof value === 'string' && /^(?:\$\{\{\s*)?false(?:\s*\}\})?$/i.test(value.trim()));
}

function commandLines(run) {
  return typeof run === 'string' ? run.split(/\r?\n/).map(line => line.trim()) : [];
}

function normalizedRepoPath(workingDirectory = '', prefix) {
  const repository = '/__repository__';
  const cwd = posix.resolve(repository, workingDirectory.replaceAll('\\', '/') || '.');
  return prefix === undefined ? cwd : posix.resolve(cwd, prefix.replaceAll('\\', '/'));
}

function isAuditCommand(line, workingDirectory = '') {
  const npm = /^(?:&\s*)?npm(?:\.cmd)?\s+(?:(?:--prefix|-C)\s+(["']?[^\s"']+["']?)\s+)?run\s+(?:--silent\s+)?audit:security\s*$/i.exec(line);
  if (npm) {
    const prefix = npm[1]?.replace(/^["']|["']$/g, '');
    return normalizedRepoPath(workingDirectory, prefix) === '/__repository__/renderer';
  }
  if (/^(?:&\s*)?node(?:\.exe)?\s+(?:\.\/)?scripts[\\/]audit-security\.mjs\s*$/i.test(line)) {
    return normalizedRepoPath(workingDirectory) === '/__repository__/renderer';
  }
  return /^(?:&\s*)?node(?:\.exe)?\s+(?:\.\/)?renderer[\\/]scripts[\\/]audit-security\.mjs\s*$/i.test(line);
}

function isPackageAuditCommand(command) {
  return typeof command === 'string' &&
    /^\s*node(?:\.exe)?\s+(?:\.\/)?scripts[\\/]audit-security\.mjs\s*$/i.test(command);
}

function auditSteps(workflow) {
  return stepsIn(workflow).filter(({ step }) =>
    commandLines(step.run).some(line => isAuditCommand(line, step['working-directory'] ?? '')),
  );
}

function safeChildEnvironment(run, exitCode) {
  const allowed = new Set(['path', 'pathext', 'systemroot', 'windir', 'comspec', 'temp', 'tmp']);
  const environment = Object.fromEntries(Object.entries(process.env).filter(([name]) => allowed.has(name.toLowerCase())));
  environment.CONTRACT_AUDIT_RUN = run;
  environment.CONTRACT_AUDIT_EXIT = String(exitCode);
  return environment;
}

function validatePowerShellAuditRun(run, workingDirectory, label) {
  const lines = commandLines(run).filter(line => line && !line.startsWith('#'));
  const auditIndexes = lines.flatMap((line, index) => isAuditCommand(line, workingDirectory) ? [index] : []);
  if (auditIndexes.length !== 1) fail(`${label} must contain exactly one direct audit invocation.`);
  const auditIndex = auditIndexes[0];
  const prelude = lines.slice(0, auditIndex).join(' ').replace(/\s+/g, ' ').trim();
  if (prelude && !/^\$ErrorActionPreference\s*=\s*(['"])Stop\1$/i.test(prelude)) {
    fail(`${label} contains commands outside the bounded audit and exit propagation contract.`);
  }
  const propagation = lines.slice(auditIndex + 1).join(' ').replace(/\s+/g, ' ').trim();
  const conditionalExit = /^if\s*\(\s*\$LASTEXITCODE(?:\s*-ne\s*0)?\s*\)\s*\{\s*exit\s+\$LASTEXITCODE\s*\}$/i;
  const directExit = /^exit\s+\$LASTEXITCODE$/i;
  if (propagation && propagation.toLowerCase() !== 'exit 0' && !directExit.test(propagation) && !conditionalExit.test(propagation)) {
    fail(`${label} contains commands outside the bounded audit and exit propagation contract.`);
  }
}

function assertAuditFailurePropagates(run, workingDirectory, repositoryRoot, label) {
  validatePowerShellAuditRun(run, workingDirectory, label);
  const cwd = resolve(repositoryRoot, workingDirectory || '.');
  const outside = relative(repositoryRoot, cwd);
  if (isAbsolute(outside) || outside === '..' || outside.startsWith('../') || outside.startsWith('..\\') || !existsSync(cwd)) {
    fail(`${label} has an invalid working directory.`);
  }
  const harness = `
$ErrorActionPreference = 'Stop'
$script:ContractAuditExit = [int]$env:CONTRACT_AUDIT_EXIT
function Invoke-ContractAuditMock {
    Write-Output '__LCG_AUDIT_MOCK_INVOKED__'
    $global:LASTEXITCODE = $script:ContractAuditExit
}
function npm { Invoke-ContractAuditMock }
function npm.cmd { Invoke-ContractAuditMock }
function node { Invoke-ContractAuditMock }
function node.exe { Invoke-ContractAuditMock }
& ([scriptblock]::Create($env:CONTRACT_AUDIT_RUN))
Write-Output '__LCG_AUDIT_RUN_RETURNED__'
exit 0
`;
  for (const exitCode of [0, 1, 2]) {
    const result = spawnSync('pwsh', ['-NoProfile', '-NonInteractive', '-Command', harness], {
      cwd,
      encoding: 'utf8',
      env: safeChildEnvironment(run, exitCode),
      timeout: 15_000,
      windowsHide: true,
    });
    if (result.error) fail(`${label} audit propagation fixture could not run: ${result.error.message}`);
    if (!Number.isInteger(result.status)) fail(`${label} audit propagation fixture ended without an exit code.`);
    if (!result.stdout.includes('__LCG_AUDIT_MOCK_INVOKED__')) {
      fail(`${label} failed without invoking the isolated audit mock: ${result.stderr.trim()}`);
    }
    if (exitCode === 0 && result.status !== 0) fail(`${label} rejected a successful audit with exit ${result.status}.`);
    if (exitCode !== 0 && result.status === 0) fail(`${label} converted audit exit ${exitCode} into success.`);
  }
}

function validateRequiredStep(jobId, job, step, label) {
  if (isLiteralFalse(job.if) || isLiteralFalse(step.if)) {
    fail(`${label} in job '${jobId}' must not be disabled with a literal false condition.`);
  }
  if (permitsContinueOnError(job['continue-on-error']) || permitsContinueOnError(step['continue-on-error'])) {
    fail(`${label} in job '${jobId}' must not continue on error.`);
  }
}

function validateAuditWorkflow(workflow, label, repositoryRoot) {
  const matches = auditSteps(workflow);
  if (matches.length === 0) fail(`${label} must execute the Renderer dependency security audit.`);
  for (const { jobId, job, step } of matches) {
    validateRequiredStep(jobId, job, step, `${label} audit`);
    if (!/^pwsh(?:\s|$)/i.test(String(step.shell ?? '').trim())) {
      fail(`${label} audit must use pwsh so failure propagation can be exercised.`);
    }
    assertAuditFailurePropagates(step.run, step['working-directory'] ?? '', repositoryRoot, `${label} audit in job '${jobId}'`);
  }
}

function permissionEntries(permissions) {
  if (permissions === undefined || permissions === null) return [];
  if (typeof permissions !== 'object' || Array.isArray(permissions)) fail('Workflow permissions must be a mapping.');
  return Object.entries(permissions).map(([name, value]) => {
    const normalized = String(value).toLowerCase();
    if (!['none', 'read', 'write'].includes(normalized)) fail(`Invalid permission value for '${name}': ${value}.`);
    return [name, normalized];
  });
}

function validateDependencyPermissions(workflow, submissionJobId) {
  const top = permissionEntries(workflow.permissions);
  if (!top.some(([name, value]) => name === 'contents' && value === 'read')) {
    fail('Dependency Security must default to contents: read.');
  }
  if (top.some(([, value]) => value === 'write')) fail('Dependency Security must not grant write permissions at workflow scope.');

  for (const [jobId, job] of Object.entries(workflow.jobs ?? {})) {
    if (job.environment !== undefined) fail(`Dependency Security job '${jobId}' must not use an environment.`);
    const entries = permissionEntries(job.permissions);
    const writes = entries.filter(([, value]) => value === 'write').map(([name]) => name);
    if (jobId === submissionJobId) {
      if (writes.length !== 1 || writes[0] !== 'contents') {
        fail('Only Gradle dependency submission may request exactly contents: write.');
      }
      for (const step of job.steps ?? []) {
        if (typeof step.uses !== 'string' || !submissionActionPrefixes.some(prefix => step.uses.startsWith(prefix))) {
          fail('The contents: write submission job may run only pinned checkout, Java setup, diagnostic upload, and Gradle submission actions.');
        }
      }
    } else if (writes.length > 0) {
      fail(`Dependency Security job '${jobId}' must not request write permissions: ${writes.join(', ')}.`);
    }
  }
}

function validateActionReference(repositoryRoot, workflowPath, reference, visitedLocalActions) {
  if (typeof reference !== 'string') fail(`Action reference in ${workflowPath} must be a string.`);
  if (reference.startsWith('./')) {
    const target = resolve(repositoryRoot, reference);
    const outside = relative(repositoryRoot, target);
    if (isAbsolute(outside) || outside === '..' || outside.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`)) {
      fail(`Local action reference escapes the repository: ${workflowPath} (${reference}).`);
    }
    if (!existsSync(target)) fail(`Local action reference does not exist: ${workflowPath} (${reference}).`);
    const realOutside = relative(realpathSync(repositoryRoot), realpathSync(target));
    if (isAbsolute(realOutside) || realOutside === '..' || realOutside.startsWith('../') || realOutside.startsWith('..\\')) {
      fail(`Local action reference resolves outside the repository: ${workflowPath} (${reference}).`);
    }
    if (statSync(target).isDirectory()) {
      const actionPath = ['action.yml', 'action.yaml'].map(file => resolve(target, file)).find(existsSync);
      if (!actionPath) fail(`Local action directory has no action.yml or action.yaml: ${workflowPath} (${reference}).`);
      const realActionPath = realpathSync(actionPath);
      const realActionOutside = relative(realpathSync(repositoryRoot), realActionPath);
      if (isAbsolute(realActionOutside) || realActionOutside === '..' ||
          realActionOutside.startsWith('../') || realActionOutside.startsWith('..\\')) {
        fail(`Local action manifest resolves outside the repository: ${workflowPath} (${reference}).`);
      }
      if (!visitedLocalActions.has(realActionPath)) {
        visitedLocalActions.add(realActionPath);
        const action = readYaml(actionPath);
        for (const step of action?.runs?.steps ?? []) {
          if (Object.hasOwn(step, 'uses')) validateActionReference(repositoryRoot, reference, step.uses, visitedLocalActions);
        }
      }
    } else if (!['.yml', '.yaml'].includes(extname(target).toLowerCase())) {
      fail(`Local reusable workflow must be YAML: ${workflowPath} (${reference}).`);
    }
    return;
  }
  if (!externalAction.test(reference)) {
    fail(`External Action is not pinned to a full commit SHA: ${workflowPath} (${reference}).`);
  }
}

function validateAllActionPins(repositoryRoot) {
  const workflowDirectory = resolve(repositoryRoot, '.github/workflows');
  const visitedLocalActions = new Set();
  for (const name of readdirSync(workflowDirectory).filter(file => ['.yml', '.yaml'].includes(extname(file).toLowerCase()))) {
    const path = resolve(workflowDirectory, name);
    const workflow = readYaml(path);
    for (const job of Object.values(workflow?.jobs ?? {})) {
      if (Object.hasOwn(job, 'uses')) validateActionReference(repositoryRoot, `.github/workflows/${name}`, job.uses, visitedLocalActions);
      for (const step of job.steps ?? []) {
        if (Object.hasOwn(step, 'uses')) validateActionReference(repositoryRoot, `.github/workflows/${name}`, step.uses, visitedLocalActions);
      }
    }
  }
}

function validateDependabot(config) {
  if (config?.version !== 2 || !Array.isArray(config.updates)) fail('Dependabot configuration must use schema version 2 with updates.');
  for (const [ecosystem, directory] of requiredDependabotDirectories) {
    if (!config.updates.some(update => update?.['package-ecosystem'] === ecosystem && update?.directory === directory)) {
      fail(`Dependabot must cover ${ecosystem} at ${directory}.`);
    }
  }
}

function validateDependencyWorkflow(workflow) {
  const reviewSteps = stepsIn(workflow).filter(({ step }) =>
    typeof step.uses === 'string' && step.uses.startsWith('actions/dependency-review-action@'));
  if (reviewSteps.length !== 1) fail('Dependency Security must contain exactly one Dependency Review action.');
  const reviewStep = reviewSteps[0];
  validateRequiredStep(reviewStep.jobId, reviewStep.job, reviewStep.step, 'Dependency Review');
  const review = reviewStep.step.with ?? {};
  const severity = String(review['fail-on-severity'] ?? '').toLowerCase();
  if (!acceptedSeverityThresholds.has(severity)) fail('Dependency Review must reject high and critical advisories or use a stricter threshold.');
  const scopes = new Set(String(review['fail-on-scopes'] ?? '').split(',').map(scope => scope.trim().toLowerCase()).filter(Boolean));
  if (scopes.size !== requiredScopes.size || [...requiredScopes].some(scope => !scopes.has(scope))) {
    fail('Dependency Review must cover runtime, development, and unknown scopes.');
  }

  const submissionSteps = stepsIn(workflow).filter(({ step }) =>
    typeof step.uses === 'string' && step.uses.startsWith('gradle/actions/dependency-submission@'));
  if (submissionSteps.length !== 1) fail('Dependency Security must contain exactly one Gradle Dependency Submission action.');
  const submission = submissionSteps[0];
  validateRequiredStep(submission.jobId, submission.job, submission.step, 'Gradle dependency submission');
  if (submission.step.with?.['dependency-graph-continue-on-failure'] !== false &&
      String(submission.step.with?.['dependency-graph-continue-on-failure']).toLowerCase() !== 'false') {
    fail('Gradle dependency submission must fail when dependency graph submission fails.');
  }
  validateDependencyPermissions(workflow, submission.jobId);

  const serialized = JSON.stringify(workflow);
  if (/\$\{\{\s*secrets\./i.test(serialized)) fail('Dependency Security must not reference environment secrets.');
  for (const { step } of stepsIn(workflow)) {
    if (typeof step.uses === 'string' &&
        /(?:softprops\/action-gh-release|actions\/create-release|ncipollo\/release-action)/i.test(step.uses)) {
      fail('Dependency Security must not invoke a release publishing action.');
    }
    if (commandLines(step.run).some(line => /^(?:&\s*)?(?:gh\s+release|git\s+tag)(?:\s|$)/i.test(line))) {
      fail('Dependency Security must not create tags or releases.');
    }
  }
}

export function validateRepository(repositoryRoot) {
  const root = resolve(repositoryRoot);
  validateDependabot(readYaml(resolve(root, '.github/dependabot.yml')));
  const packageJson = JSON.parse(readFileSync(resolve(root, 'renderer/package.json'), 'utf8').replace(/^\uFEFF/, ''));
  if (!isPackageAuditCommand(packageJson.scripts?.['audit:security'])) {
    fail('Renderer audit:security must execute scripts/audit-security.mjs directly.');
  }

  const ci = readYaml(resolve(root, '.github/workflows/ci.yml'));
  const release = readYaml(resolve(root, '.github/workflows/release.yml'));
  const dependencyWorkflow = readYaml(resolve(root, '.github/workflows/dependency-security.yml'));
  validateDependencyWorkflow(dependencyWorkflow);
  validateAllActionPins(root);
  validateAuditWorkflow(ci, 'Android Quality Gate', root);
  validateAuditWorkflow(release, 'Production Release Candidate', root);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const root = resolve(process.argv[2] ?? dirname(dirname(dirname(process.argv[1]))));
  validateRepository(root);
  console.log('Dependency security contract PASS.');
}
