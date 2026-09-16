import fs from 'node:fs';
const path = 'renderer/schema/render-spec-v1.schema.json';
const schema = JSON.parse(fs.readFileSync(path, 'utf8'));
const c = schema.properties.canvas.properties;
c.autoWidth = {type:'boolean'};
c.landscape = {type:'object', additionalProperties:false, required:['autoLyricsWidth','lyricsWidth','autoHeight','requestedHeight'], properties:{
  autoLyricsWidth:{type:'boolean'}, lyricsWidth:{type:'integer',minimum:520,maximum:1280}, autoHeight:{type:'boolean'}, requestedHeight:{type:'integer',minimum:720,maximum:3600}
}};
c.height.minimum=640; c.height.maximum=6400;
const t = schema.properties.typography.properties;
t.lineHeight.minimum=1.5; t.lineHeight.maximum=2.1;
Object.assign(t, { latinFontFamily:{type:'string',minLength:1,maxLength:200}, customFontAsset:{anyOf:[{type:'null'},{type:'string',pattern:'^[a-f0-9]{64}\\.(ttf|otf|woff2?)$'}]}, customFontEnabled:{type:'boolean'}, fontWeight:{type:'integer',minimum:100,maximum:900}, fontItalic:{type:'boolean'}, separatorStyle:{enum:['dot','line']} });
for(const rule of schema.allOf) {
 const when=rule.if.properties.canvas?.properties;
 const then=rule.then.properties.canvas?.properties;
 if(when?.layoutMode?.const==='portrait') {then.height.minimum=640;then.height.maximum=6400;}
 if(when?.layoutMode?.const==='landscape') {then.height.maximum=6400;delete then.autoHeight;}
 if(when?.autoHeight?.const===true) delete then.layoutMode;
}
fs.writeFileSync(path,JSON.stringify(schema,null,2)+'\n');
for(const name of fs.readdirSync('renderer/fixtures').filter(n=>n.endsWith('.json'))) {
 const path=`renderer/fixtures/${name}`, spec=JSON.parse(fs.readFileSync(path,'utf8'));
 if(spec.typography) {spec.typography.lineHeight=1.8;fs.writeFileSync(path,JSON.stringify(spec,null,2)+'\n');}
}
const goldenPath='renderer/golden/cases.json';
const golden=JSON.parse(fs.readFileSync(goldenPath,'utf8'));
for(const entry of golden.cases) if(entry.overrides.typography?.lineHeight) entry.overrides.typography.lineHeight=1.8;
fs.writeFileSync(goldenPath,JSON.stringify(golden,null,2)+'\n');
