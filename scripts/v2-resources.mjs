import fs from 'node:fs';
const root='app/src/main/java/com/qrzzzz/lyricscard/ui/';
const entries={
 '自动歌词宽度':'v2_auto_lyrics_width','歌词宽度':'v2_lyrics_width','自动高度':'editor_auto_height','期望高度（内容过长时自动扩展）':'v2_requested_height','自动宽度':'v2_auto_width',
 '字体已导入':'v2_font_imported','字体导入失败':'v2_font_failed','正在导入…':'v2_font_importing','导入自定义字体':'v2_font_import','使用导入字体':'v2_font_use','西文字体（本机可用字体名称）':'v2_latin_font','字重':'v2_font_weight','斜体':'v2_font_italic'
};
for(const name of ['EditorLayoutPanel.kt','CustomFontControls.kt']) {
 let text=fs.readFileSync(root+name,'utf8');
 for(const [value,key] of Object.entries(entries)) text=text.replaceAll(`"${value}"`,`${['字体已导入','字体导入失败'].includes(value)?'context.getString':'stringResource'}(R.string.${key})`);
 if(name==='CustomFontControls.kt') text=text.replace('package com.qrzzzz.lyricscard.ui','package com.qrzzzz.lyricscard.ui\n\nimport com.qrzzzz.lyricscard.R\nimport androidx.compose.ui.res.stringResource');
 fs.writeFileSync(root+name,text);
}
const path='app/src/main/res/values/strings.xml';
let xml=fs.readFileSync(path,'utf8');
for(const [value,key] of Object.entries(entries)) if(!xml.includes(`name="${key}"`)) xml=xml.replace('</resources>',`    <string name="${key}">${value}</string>\n</resources>`);
fs.writeFileSync(path,xml);
const test='app/src/test/java/com/qrzzzz/lyricscard/model/RenderSpecValidationTest.kt';
fs.writeFileSync(test,fs.readFileSync(test,'utf8').replace('height = 4_000','height = 7_000'));
