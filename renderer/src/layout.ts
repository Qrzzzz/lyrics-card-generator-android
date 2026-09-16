import { chooseAutoWidth, getAutoWidthCandidates } from './desktop/auto-width';
import { measureAutoWidthLine } from './desktop/auto-width-dom';
import { createLandscapeLayoutPlan, DEFAULT_LANDSCAPE_LAYOUT_SETTINGS, getLandscapeLyricsWidthCandidates } from './desktop/landscape-plan';
import type { RenderSpec } from './types';
import { getPortraitLayout } from './desktop/card-layout-engine';
import type { CardStyle } from './desktop/types';

export function portraitLayout(spec: RenderSpec, width = spec.canvas.width) {
  // Only these CardStyle fields are read by the shared portrait geometry engine.
  return getPortraitLayout({width, height:spec.canvas.height}, {
    contentMode:spec.content.mode, showCover:spec.visibility.showCover,
    showSongInfo:spec.visibility.showSongInfo, showAlbumName:spec.visibility.showAlbum,
    showSharedBy:spec.visibility.showSharedBy, sharedByText:spec.branding.sharedByName,
    showGeneratedWatermark:spec.visibility.showGeneratedWatermark, align:spec.typography.alignment
  } as CardStyle, {source:spec.song.source, album:spec.song.album});
}

/** Measure the same loaded-font lyric tree used by preview and export. */
export function measureLayout(node: HTMLElement, spec: RenderSpec): RenderSpec {
  if (spec.content.mode !== 'lyrics' || spec.canvas.ratio !== 'custom') return spec;
  const lyrics = node.querySelector<HTMLElement>('[data-card-lyrics]');
  if (!lyrics) throw new Error('Missing lyrics measurement tree');
  const clone = lyrics.cloneNode(true) as HTMLElement;
  const host = node.cloneNode(false) as HTMLElement;
  host.removeAttribute('data-export-card');
  Object.assign(host.style, { position:'fixed', left:'-20000px', top:'0', transform:'none', height:'auto', visibility:'hidden' });
  host.append(clone); document.body.append(host);
  clone.style.height='auto';
  const lines = [...clone.querySelectorAll<HTMLElement>('.lyric-line,.translation-line')];
  lines.forEach((line, i) => { line.dataset.autoWidthLineIndex=String(i); line.dataset.autoWidthLine=line.classList.contains('translation-line')?'translation':'lyric'; });
  const metrics = () => lines.map(line => measureAutoWidthLine(line)).filter((line): line is NonNullable<typeof line> => line !== null);
  try {
    if (spec.canvas.layoutMode === 'portrait' && spec.canvas.autoWidth) {
      const samples = getAutoWidthCandidates().map(canvasWidth => {
        host.style.width=`${canvasWidth}px`; clone.style.width=`${portraitLayout(spec,canvasWidth).lyricsRect.width}px`;
        return {canvasWidth, lines:metrics()};
      });
      const decision=chooseAutoWidth(samples, spec.canvas.width);
      return {...spec,canvas:{...spec.canvas,width:decision.width}};
    }
    if (spec.canvas.layoutMode === 'landscape') {
      const settings=spec.canvas.landscape ?? DEFAULT_LANDSCAPE_LAYOUT_SETTINGS;
      const lyricsCandidates=getLandscapeLyricsWidthCandidates(settings).map(lyricsWidth => {
        host.style.width=`${lyricsWidth}px`; clone.style.width=`${lyricsWidth}px`;
        return {lyricsWidth,naturalHeight:clone.scrollHeight,lines:metrics()};
      });
      const info=node.querySelector<HTMLElement>('[data-card-song-info]');
      const footer=node.querySelector<HTMLElement>('[data-card-footer]');
      const image=node.querySelector<HTMLImageElement>('.cover-art img');
      const aspect=image?.naturalWidth && image.naturalHeight ? image.naturalWidth/image.naturalHeight : 1;
      const plan=createLandscapeLayoutPlan({measurementKey:JSON.stringify(spec),settings,lyricsCandidates,
        left:{coverWidth:520,coverHeight:520/aspect,metadataWidth:520,metadataHeight:info?.scrollHeight ?? 0, accessoriesWidth:520,accessoriesHeight:footer?.scrollHeight ?? 0}});
      if (!plan) throw new Error('Could not resolve landscape layout');
      if (plan.canvas.width>3000 || plan.canvas.height>6400) throw new Error('Content exceeds the supported canvas size');
      return {...spec,canvas:{...spec.canvas,...plan.canvas,layoutPlan:plan}};
    }
    return spec;
  } finally {host.remove();}
}
