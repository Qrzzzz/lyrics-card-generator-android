import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

const transientCodes = new Set([
  'ETIMEDOUT', 'ESOCKETTIMEDOUT', 'ECONNRESET', 'ECONNREFUSED',
  'ENOTFOUND', 'EAI_AGAIN', 'ENETUNREACH', 'EHOSTUNREACH',
  'E429', 'E500', 'E502', 'E503', 'E504',
]);
const transientStatuses = new Set([429, 500, 502, 503, 504]);
const severities = ['info', 'low', 'moderate', 'high', 'critical'];
const isRecord = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const isText = value => typeof value === 'string' && value.length > 0;
const isCount = value => Number.isSafeInteger(value) && value >= 0;

function validAuditReport(report) {
  if (report.auditReportVersion !== 2 || !isRecord(report.metadata)
    || !isRecord(report.metadata.vulnerabilities) || !isRecord(report.metadata.dependencies)
    || !isRecord(report.vulnerabilities)) return false;
  const counts = report.metadata.vulnerabilities;
  if (![...severities, 'total'].every(key => isCount(counts[key]))
    || severities.reduce((sum, key) => sum + counts[key], 0) !== counts.total
    || !['prod', 'dev', 'optional', 'peer', 'peerOptional', 'total']
      .every(key => isCount(report.metadata.dependencies[key]))) return false;
  // npm counts vulnerable packages, not advisory entries or overlapping
  // dependency categories. Reconcile the package details with every severity.
  const actual = Object.fromEntries(severities.map(key => [key, 0]));
  for (const [name, detail] of Object.entries(report.vulnerabilities)) {
    if (!isRecord(detail) || detail.name !== name || !severities.includes(detail.severity)
      || typeof detail.isDirect !== 'boolean' || !isText(detail.range)
      || !Array.isArray(detail.nodes) || detail.nodes.length === 0 || !detail.nodes.every(isText)
      || !Array.isArray(detail.effects) || !detail.effects.every(isText)
      || !Array.isArray(detail.via) || detail.via.length === 0) return false;
    const fix = detail.fixAvailable;
    if (typeof fix !== 'boolean' && !(isRecord(fix) && isText(fix.name)
      && isText(fix.version) && typeof fix.isSemVerMajor === 'boolean')) return false;
    for (const via of detail.via) {
      // Strings identify a metavulnerability's underlying package; objects
      // describe direct advisories. Neither may hide a stronger severity.
      const advisory = isText(via) ? report.vulnerabilities[via] : via;
      if (!isRecord(advisory) || !severities.includes(advisory.severity)
        || severities.indexOf(advisory.severity) > severities.indexOf(detail.severity)) return false;
      if (!isText(via) && ((!isCount(advisory.source) && !isText(advisory.source))
        || !['name', 'dependency', 'title', 'url', 'range'].every(key => isText(advisory[key])))) return false;
    }
    actual[detail.severity]++;
  }
  return severities.every(key => actual[key] === counts[key]);
}

export function classifyAudit(result) {
  const stdout = result.stdout?.trim() ?? '';
  let report;
  try { report = JSON.parse(stdout); } catch { /* Only an empty transport response may retry. */ }
  const codes = [report?.error?.code, report?.code, result.error?.code].filter(value => value !== undefined);
  const statuses = [report?.statusCode, report?.error?.statusCode].filter(value => value !== undefined);
  const diagnostic = [result.stderr, result.error?.message, report?.message,
    report?.error?.summary, report?.error?.detail].filter(isText).join('\n');
  // Structured/authentication failures take precedence over ambiguous prose.
  if (/\b(?:E401|E403|EAUTH|ENEEDAUTH|EOTP)\b|\b(?:401|403)\s+(?:Unauthorized|Forbidden)\b/i.test(diagnostic)
    || codes.some(code => !transientCodes.has(code) && code !== 'FETCH_ERROR')
    || statuses.some(status => !transientStatuses.has(status))) return 'invalid';
  const hasReport = isRecord(report) && ['auditReportVersion', 'metadata', 'vulnerabilities']
    .some(key => Object.hasOwn(report, key));
  if (hasReport) {
    if (!validAuditReport(report) || report.error || result.error || codes.length || statuses.length) return 'invalid';
    const counts = report.metadata.vulnerabilities;
    if (counts.high || counts.critical) return 'vulnerable';
    return result.status === 0 ? 'pass' : 'invalid';
  }
  if (stdout && (!isRecord(report) || !(isRecord(report.error) || isText(report.message)))) return 'invalid';
  if (result.status === 0) return 'invalid';
  const networkMessage = /network timeout|request timed? ?out|socket hang up|\b(?:ETIMEDOUT|ECONNRESET|EAI_AGAIN|ENOTFOUND)\b/i.test(diagnostic);
  return codes.some(code => transientCodes.has(code)) || statuses.some(status => transientStatuses.has(status))
    || networkMessage ? 'transient' : 'invalid';
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
