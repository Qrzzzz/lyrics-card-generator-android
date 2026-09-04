import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

const transientCodes = new Set([
  'ETIMEDOUT', 'ESOCKETTIMEDOUT', 'ECONNRESET', 'ECONNREFUSED',
  'ENOTFOUND', 'EAI_AGAIN', 'ENETUNREACH', 'EHOSTUNREACH',
  'E429', 'E500', 'E502', 'E503', 'E504',
]);

export function classifyAudit(result) {
  let report;
  try { report = JSON.parse(result.stdout); } catch { /* Invalid reports never pass. */ }
  const counts = report?.metadata?.vulnerabilities;
  if (counts && ['info', 'low', 'moderate', 'high', 'critical', 'total']
    .every(key => Number.isInteger(counts[key]) && counts[key] >= 0)) {
    if (counts.high > 0 || counts.critical > 0) return 'vulnerable';
    if (result.status === 0 && !result.error && !report.error) return 'pass';
  }
  const code = report?.error?.code ?? result.error?.code;
  const diagnostic = [result.stderr, result.error?.message, report?.error?.summary, report?.error?.detail].filter(Boolean).join('\n');
  const networkMessage = /network timeout|request timed? ?out|socket hang up|\b(?:ETIMEDOUT|ECONNRESET|EAI_AGAIN|ENOTFOUND)\b/i.test(diagnostic);
  return transientCodes.has(code) || networkMessage ? 'transient' : 'invalid';
}

export async function auditSecurity({
  run = () => {
    const args = ['audit', '--json', '--audit-level=high', '--fetch-timeout=30000', '--fetch-retries=0'];
    return process.env.npm_execpath
      ? spawnSync(process.execPath, [process.env.npm_execpath, ...args], { encoding: 'utf8', timeout: 75000 })
      : spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', args,
        { encoding: 'utf8', timeout: 75000, shell: process.platform === 'win32' });
  },
  sleep = ms => new Promise(resolve => setTimeout(resolve, ms)),
  log = message => console.log(message),
} = {}) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    const result = run();
    log(result.stdout || result.stderr || result.error?.message || 'No npm audit report.');
    const outcome = classifyAudit(result);
    if (outcome === 'pass') return 0;
    if (outcome === 'vulnerable') {
      log('SECURITY FAIL: high/critical advisories found; no retry.');
      return 1;
    }
    if (outcome !== 'transient' || attempt === 3) {
      log(`AUDIT UNAVAILABLE: ${outcome} report after ${attempt} attempt(s); security has not passed.`);
      return 2;
    }
    log(`Transient audit service failure (${attempt}/3); retrying without changing dependencies.`);
    await sleep(attempt * 2000);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await auditSecurity();
}
