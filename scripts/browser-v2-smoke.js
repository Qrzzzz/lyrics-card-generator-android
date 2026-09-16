async (page) => {
  const result = await page.evaluate(async () => {
    const {DEFAULT_RENDER_SPEC} = await import('/src/defaultSpec.ts');
    window.__v2Messages=[];
    window.LyricsCardNative={postMessage: text => { const e=JSON.parse(text); if(e.type!=='exportChunk') window.__v2Messages.push(e); }};
    const request=async(type,payload) => {
      const requestId=`qa-${type}-${Date.now()}`;
      window.LyricsCardRenderer.receive({protocolVersion:1,requestId,type,payload});
      const start=Date.now();
      while(Date.now()-start<45000) {
        const done=window.__v2Messages.find(m=>m.requestId===requestId && ['specApplied','measured','exportCompleted','renderError'].includes(m.type));
        if(done) {if(done.type==='renderError')throw new Error(JSON.stringify(done)); return done.payload;}
        await new Promise(r=>setTimeout(r,50));
      }
      throw new Error('Timed out '+type);
    };
    const results=[];
    for(const mode of ['portrait','landscape']) {
      let spec=structuredClone(DEFAULT_RENDER_SPEC);
      spec.canvas={...spec.canvas,layoutMode:mode,ratio:'custom',width:mode==='portrait'?1040:1920,height:1080,autoHeight:true,autoWidth:true};
      await request('setSpec',spec);
      const measured=await request('measure',spec);
      spec.canvas={...spec.canvas,...measured};
      await request('setSpec',spec);
      const node=document.querySelector('[data-export-card]');
      if(node.offsetWidth!==measured.width || node.offsetHeight!==measured.height) throw new Error('Preview measure mismatch');
      for(const format of ['png','webp','jpg']) {
        const scale=format==='png'?1:format==='webp'?1.4:2;
        spec.canvas.exportFormat=format;
        const exported=await request('exportPng',{spec,pixelRatio:scale});
        if(exported.width!==Math.floor(measured.width*scale) || exported.height!==Math.floor(measured.height*scale)) throw new Error('Export geometry mismatch');
        if(exported.mimeType!==({png:'image/png',webp:'image/webp',jpg:'image/jpeg'})[format]) throw new Error('Wrong MIME');
        results.push({mode,format,scale,...exported});
      }
    }
    return results;
  });
  console.log(JSON.stringify(result));
  await page.screenshot({path:'output/playwright/android-v2-landscape.png'});
}
