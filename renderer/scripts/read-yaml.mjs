import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseDocument } from 'yaml';

export function readYaml(path) {
  const document = parseDocument(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''), {
    prettyErrors: true,
    strict: true,
    uniqueKeys: true,
  });
  if (document.errors.length > 0) {
    throw new Error(document.errors.map(error => error.message).join('\n'));
  }
  return document.toJS({ maxAliasCount: 100 });
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  if (process.argv.length !== 3) throw new Error('Usage: node read-yaml.mjs <yaml-path>');
  process.stdout.write(`${JSON.stringify(readYaml(resolve(process.argv[2])))}\n`);
}
