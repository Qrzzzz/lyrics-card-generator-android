import { appendFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

export function needsUnicodeSmoke(event, paths = []) {
  if (event !== 'pull_request') return true;
  return paths.some(path => /^(scripts\/|gradle\/|buildSrc\/|\.github\/workflows\/ci\.yml$|(?:app\/)?build\.gradle(?:\.kts)?$|settings\.gradle(?:\.kts)?$|gradle\.properties$|gradlew(?:\.bat)?$|renderer\/(?:package(?:-lock)?\.json|vite\.config\.ts|tsconfig\.json|scripts\/)|app\/src\/test\/.*LyricTextCleanerTest\.)/.test(path));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let paths = [];
  if (process.env.GITHUB_EVENT_NAME === 'pull_request') {
    const { PR_BASE_SHA: base, PR_HEAD_SHA: head } = process.env;
    if (![base, head].every(sha => /^[a-f0-9]{40}$/.test(sha ?? ''))) {
      throw new Error('PR scope requires exact base and head SHAs.');
    }
    paths = execFileSync('git', ['diff', '--name-only', '--no-renames', `${base}...${head}`],
      { encoding: 'utf8' }).trim().split(/\r?\n/).filter(Boolean);
  }
  const needed = needsUnicodeSmoke(process.env.GITHUB_EVENT_NAME, paths);
  console.log(needed ? 'Unicode path regression is required.' : 'No Unicode build inputs changed; dedicated smoke is not required.');
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, `unicode=${needed}\n`);
}
