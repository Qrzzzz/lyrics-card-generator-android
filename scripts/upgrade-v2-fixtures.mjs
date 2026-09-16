// One-time development conversion of test fixtures, never persisted user projects.
import fs from 'node:fs';
import { createLyricDocumentV2 } from '../renderer/src/desktop/lyrics-document-v2.ts';
const schemaPath = 'renderer/schema/render-spec-v1.schema.json';
const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
schema.title = 'Lyrics Card RenderSpec v2';
schema.properties.schemaVersion.const = 2;
const content = schema.properties.content;
if (!content.required.includes('lyricDocument')) content.required.push('lyricDocument');
content.properties.lyricDocument = { type: 'object', required: ['schemaVersion', 'id', 'revision', 'blocks'], properties: {
  schemaVersion: { const: 2 }, id: { type: 'string', minLength: 1, maxLength: 128 }, revision: { type: 'integer', minimum: 0 },
  blocks: { type: 'array', maxItems: 400, items: { type: 'object', required: ['id', 'units'], properties: {
    id: { type: 'string', minLength: 1, maxLength: 128 }, units: { type: 'array', maxItems: 800, items: { type: 'object', required: ['id','source'], properties: {
      id: { type: 'string', minLength: 1, maxLength: 128 }, source: { type: 'array', maxItems: 400, items: {type:'string', maxLength:200000} }, translation: { type: 'array', maxItems: 400, items: {type:'string', maxLength:200000} }
    } } }
  } } }
} };
fs.writeFileSync(schemaPath, JSON.stringify(schema, null, 2) + '\n');
for (const name of fs.readdirSync('renderer/fixtures').filter(n => n.endsWith('.json'))) {
  const path = `renderer/fixtures/${name}`;
  const spec = JSON.parse(fs.readFileSync(path, 'utf8'));
  if (!spec.content) continue;
  let id = 0;
  spec.schemaVersion = 2;
  spec.content.lyricDocument = createLyricDocumentV2(spec.content.lyrics, spec.content.translation, { idFactory: prefix => `${prefix}-${++id}` });
  fs.writeFileSync(path, JSON.stringify(spec, null, 2) + '\n');
}
