import { appendFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

export const scopeNames = ['renderer', 'android', 'contracts', 'audit', 'full', 'unicode'];

const documentationPath = /^(?:docs\/|(?:renderer\/)?README(?:\.en)?\.md$|CHANGELOG\.md$|LICENSE$|PRIVACY\.md$|THIRD_PARTY_NOTICES\.md$|RELEASE_CHECKLIST\.md$)/i;
const conservativePath = /^(?:scripts\/|config\/|docs\/releases\/|gradle\/|buildSrc\/|\.github\/(?:workflows\/|dependabot\.ya?ml$)|(?:app\/)?build\.gradle(?:\.kts)?$|settings\.gradle(?:\.kts)?$|gradle\.properties$|gradlew(?:\.bat)?$|release-signing\.properties\.example$|app\/(?:proguard-rules\.pro|test-proguard-rules\.pro|schemas\/)|app\/src\/[^/]+\/(?:AndroidManifest\.xml|res\/xml\/)|app\/src\/(?:main|test)\/.*(?:\/renderer\/|RenderSpec)|renderer\/(?:package(?:-lock)?\.json|vite\.config\.ts|tsconfig\.json|renderer-manifest\.json|schema\/|scripts\/|src\/(?:generated\/|spec\.ts$|transport\.ts$|types\.ts$|runtime\.ts$)))/i;
const ordinaryRendererPath = /^renderer\/(?:src\/|tests\/|fixtures\/|public\/fonts\/|golden\/|index\.html$)/i;
const ordinaryAndroidPath = /^app\/src\/(?:main\/(?:java|res)\/|(?:production|debug)\/(?:java|res)\/|test\/(?:java|kotlin)\/)/i;

function emptyScope() {
  return Object.fromEntries(scopeNames.map(name => [name, false]));
}

function fullScope() {
  return Object.fromEntries(scopeNames.map(name => [name, true]));
}

function normalizePath(path) {
  return String(path).replaceAll('\\', '/').replace(/^\.\//, '');
}

export function needsUnicodeSmoke(event, paths = []) {
  if (event !== 'pull_request') return true;
  return paths.some(rawPath => /^(scripts\/|gradle\/|buildSrc\/|\.github\/workflows\/ci\.yml$|(?:app\/)?build\.gradle(?:\.kts)?$|settings\.gradle(?:\.kts)?$|gradle\.properties$|gradlew(?:\.bat)?$|renderer\/(?:package(?:-lock)?\.json|vite\.config\.ts|tsconfig\.json|scripts\/)|app\/src\/test\/.*LyricTextCleanerTest\.)/.test(normalizePath(rawPath)));
}

export function classifyCiScope(event, paths = []) {
  if (event !== 'pull_request') return fullScope();
  const scope = emptyScope();
  for (const rawPath of paths) {
    const path = normalizePath(rawPath);
    if (!path) continue;
    if (conservativePath.test(path)) return fullScope();
    if (documentationPath.test(path)) continue;
    if (ordinaryRendererPath.test(path)) {
      scope.renderer = true;
      continue;
    }
    if (ordinaryAndroidPath.test(path)) {
      scope.android = true;
      continue;
    }
    return fullScope();
  }
  scope.unicode = needsUnicodeSmoke(event, paths);
  return scope;
}

const childJobs = {
  renderer: 'renderer',
  android: 'android',
  contracts: 'contracts',
  audit: 'audit',
  unicode: 'unicode-path-jvm-smoke',
};

export function validateCiSummary(event, needs) {
  if (!needs || typeof needs !== 'object' || Array.isArray(needs)) throw new Error('CI summary requires the complete needs object.');
  for (const [jobId, job] of Object.entries(needs)) {
    if (!job || typeof job !== 'object' || !['success', 'skipped'].includes(job.result)) {
      throw new Error(`CI child '${jobId}' did not complete safely: ${job?.result ?? 'missing result'}.`);
    }
  }
  const scopeJob = needs.scope;
  if (!scopeJob || scopeJob.result !== 'success' || !scopeJob.outputs || typeof scopeJob.outputs !== 'object') {
    throw new Error('CI scope did not complete successfully with outputs.');
  }
  const scope = {};
  for (const name of scopeNames) {
    const value = scopeJob.outputs[name];
    if (!['true', 'false'].includes(value)) throw new Error(`CI scope output '${name}' is missing or invalid.`);
    scope[name] = value === 'true';
  }
  if (scope.full && scopeNames.some(name => name !== 'full' && !scope[name])) {
    throw new Error('Full CI scope must require every child gate.');
  }
  if (event !== 'pull_request' && scopeNames.some(name => !scope[name])) {
    throw new Error(`CI event '${event || 'unknown'}' must use full scope.`);
  }
  for (const [output, jobId] of Object.entries(childJobs)) {
    const job = needs[jobId];
    if (!job) throw new Error(`CI summary is missing child '${jobId}'.`);
    if (output === 'unicode' || scope[output]) {
      if (job.result !== 'success') throw new Error(`Required CI child '${jobId}' did not succeed: ${job.result}.`);
    }
  }
  return true;
}

function writeScopeOutputs(scope) {
  const output = scopeNames.map(name => `${name}=${scope[name]}`).join('\n') + '\n';
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, output);
  console.log(`CI scope: ${scopeNames.map(name => `${name}=${scope[name]}`).join(', ')}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv[2] === '--summary') {
    const needs = JSON.parse(process.env.CI_NEEDS_JSON ?? '');
    validateCiSummary(process.env.GITHUB_EVENT_NAME, needs);
    console.log('CI required summary PASS.');
  } else {
    let paths = [];
    if (process.env.GITHUB_EVENT_NAME === 'pull_request') {
      const { PR_BASE_SHA: base, PR_HEAD_SHA: head } = process.env;
      if (![base, head].every(sha => /^[a-f0-9]{40}$/.test(sha ?? ''))) {
        throw new Error('PR scope requires exact base and head SHAs.');
      }
      paths = execFileSync('git', ['diff', '--name-only', '--no-renames', '-z', `${base}...${head}`],
        { encoding: 'utf8' }).split('\0').filter(Boolean);
    }
    writeScopeOutputs(classifyCiScope(process.env.GITHUB_EVENT_NAME, paths));
  }
}
