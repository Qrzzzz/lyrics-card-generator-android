async (page) => {
  const cards=page.locator('[data-export-card="true"]');
  const count=await cards.count();
  for(let index=0;index<count;index++) {
    const card=cards.nth(index);
    const box=await card.boundingBox();
    if(box && box.x>=0 && box.y>=0) {
      await card.screenshot({path:'output/playwright/desktop-v2-reference-portrait.png'});
      return await card.evaluate(node=>({width:node.offsetWidth,height:node.offsetHeight}));
    }
  }
  throw new Error('Visible desktop reference not found');
}
