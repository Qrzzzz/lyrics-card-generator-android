import { useMemo } from 'react';
import { createColorFieldMesh, createColorFieldPlan } from './desktop/spatial-color-field';
import { DEFAULT_PALETTE } from './desktop/palette-background';
import { BACKGROUND_GRID_SIZE_BY_DENSITY } from './desktop/background-grid';
import type { RenderSpec } from './types';

export function SpatialBackground({spec}:{spec:RenderSpec}) {
  const palette=spec.visual.palette;
  const {plan,mesh}=useMemo(() => {
    const full=palette.extracted ?? {...DEFAULT_PALETTE,primary:palette.dominant,secondary:palette.secondary,accent:palette.accent,colors:[palette.dominant,palette.secondary,palette.accent]};
    const plan=createColorFieldPlan({width:spec.canvas.width,height:spec.canvas.height,palette:full});
    return {plan,mesh:createColorFieldMesh(plan)};
  },[palette,spec.canvas.width,spec.canvas.height]);
  const filterId=`field-${plan.seed.toString(16)}`;
  return <div className="card-background" data-palette-field={plan.topology} data-palette-field-seed={plan.seed} aria-hidden="true">
    <svg style={{position:'absolute',inset:0,width:'100%',height:'100%'}} viewBox={`0 0 ${mesh.viewWidth} ${mesh.viewHeight}`} preserveAspectRatio="none" focusable="false">
      <rect width={mesh.viewWidth} height={mesh.viewHeight} fill={plan.baseColor}/>
      <defs><filter id={filterId} x={-mesh.cellSize*2} y={-mesh.cellSize*2} width={mesh.viewWidth+mesh.cellSize*4} height={mesh.viewHeight+mesh.cellSize*4} filterUnits="userSpaceOnUse" colorInterpolationFilters="sRGB"><feGaussianBlur stdDeviation={mesh.blur} edgeMode="duplicate"/></filter></defs>
      <g filter={`url(#${filterId})`}>{mesh.cells.map(cell=><rect key={cell.key} x={cell.x} y={cell.y} width={cell.width} height={cell.height} fill={cell.color}/>)}</g>
    </svg>
    {spec.visual.gridEnabled ? <div data-card-fine-grid="true" style={{position:'absolute',inset:0,opacity:.1,backgroundImage:'linear-gradient(rgba(255,255,255,.34) 1px, transparent 1px),linear-gradient(90deg, rgba(255,255,255,.34) 1px, transparent 1px)',backgroundSize:`${BACKGROUND_GRID_SIZE_BY_DENSITY[spec.visual.gridDensity]}px ${BACKGROUND_GRID_SIZE_BY_DENSITY[spec.visual.gridDensity]}px`}}/>:null}
  </div>;
}
