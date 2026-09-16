import { expect, it } from 'vitest';
import { analyzePalettePixels } from '../src/desktop/palette-extraction';
import { createColorFieldMesh, createColorFieldPlan } from '../src/desktop/spatial-color-field';

it('retains cover spatial analysis and deterministic aspect-specific composition', () => {
  const pixels=new Uint8ClampedArray(16*16*4);
  for(let y=0;y<16;y++) for(let x=0;x<16;x++) pixels.set(x<8?[230,30,40,255]:[20,80,220,255],(y*16+x)*4);
  const palette=analyzePalettePixels(pixels,16,16);
  expect(palette.analysis?.regions.length).toBeGreaterThan(1);
  const square=createColorFieldPlan({width:1080,height:1080,palette});
  const doubled=createColorFieldPlan({width:2160,height:2160,palette});
  expect(doubled.seed).toBe(square.seed);
  expect(createColorFieldMesh(square)).toEqual(createColorFieldMesh(square));
  expect(createColorFieldPlan({width:1920,height:1080,palette}).topology).not.toBe(createColorFieldPlan({width:1080,height:1920,palette}).topology);
});
