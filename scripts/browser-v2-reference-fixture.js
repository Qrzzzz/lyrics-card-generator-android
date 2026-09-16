async (page) => {
  await page.evaluate(async () => {
    const {DEFAULT_RENDER_SPEC}=await import('/src/defaultSpec.ts');
    const {createLyricDocumentV2}=await import('/src/desktop/lyrics-document-v2.ts');
    const {DEFAULT_PALETTE}=await import('/src/desktop/palette-background.ts');
    const spec=structuredClone(DEFAULT_RENDER_SPEC);
    spec.song.album='Android 2.0';
    spec.content.lyrics='晚风把城市写成一封信';
    spec.content.translation=''; spec.content.translationEnabled=false;
    spec.content.lyricDocument=createLyricDocumentV2(spec.content.lyrics,'');
    spec.canvas={...spec.canvas,layoutMode:'portrait',ratio:'custom',width:1040,height:640,autoWidth:true,autoHeight:true};
    spec.typography={...spec.typography,lyricSize:60,translationScale:.75,textColorMode:'preset',textColorPreset:'white'};
    spec.visual.gridEnabled=false;
    spec.visual.palette={dominant:DEFAULT_PALETTE.primary,secondary:DEFAULT_PALETTE.secondary,accent:DEFAULT_PALETTE.accent,extracted:DEFAULT_PALETTE};
    spec.visibility={...spec.visibility,showPlatformBadge:false,showSharedBy:false,showGeneratedWatermark:false};
    await new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(new Error('Reference fixture timeout')),20000);
      window.LyricsCardNative={postMessage:text=>{const message=JSON.parse(text); if(message.type==='specApplied'){clearTimeout(timer);resolve();} if(message.type==='renderError'){clearTimeout(timer);reject(new Error(text));}}};
      window.LyricsCardRenderer.receive({protocolVersion:1,requestId:'reference-fixture',type:'setSpec',payload:spec});
    });
  });
  await page.locator('[data-export-card]').screenshot({path:'output/playwright/android-v2-reference-portrait.png'});
  return await page.locator('[data-export-card]').evaluate(node=>({width:node.offsetWidth,height:node.offsetHeight}));
}
