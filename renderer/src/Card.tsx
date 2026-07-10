import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { isDarkColor, mixColors, resolveTextColor, withAlpha } from "./color";
import { resolveCoverAssetUrl } from "./spec";
import type { RenderSpec, SongSource, TextAlignment } from "./types";

type CardCssProperties = CSSProperties & Record<`--${string}`, string | number>;

const GRID_SIZES = {
  sparse: 72,
  medium: 56,
  dense: 40
} as const;

export function LyricsCard({ spec }: { spec: RenderSpec }) {
  const textColor = resolveTextColor(spec);
  const darkText = isDarkColor(textColor);
  const lines = useMemo(() => splitUsefulLines(spec.content.lyrics), [spec.content.lyrics]);
  const translations = useMemo(() => splitUsefulLines(spec.content.translation), [spec.content.translation]);
  const source = spec.branding.platform === "unknown" ? spec.song.source : spec.branding.platform;
  const showHeader =
    spec.content.mode === "lyrics" && (spec.visibility.showCover || spec.visibility.showSongInfo);
  const showFooter =
    (spec.visibility.showPlatformBadge && source !== "unknown") ||
    (spec.visibility.showSharedBy && spec.branding.sharedByName.trim().length > 0) ||
    spec.visibility.showGeneratedWatermark;
  const lyricSize = fitLyricSize(spec, Math.max(1, lines.length), translations.length > 0, showHeader, showFooter);
  const serif =
    spec.typography.fontScheme === "serif-heavy" ||
    spec.typography.fontScheme === "system-serif" ||
    /serif|song|宋/i.test(spec.typography.fontFamily);
  const palette = spec.visual.palette;
  const primary = mixColors(palette.dominant, "#050508", 0.34);
  const secondary = mixColors(palette.secondary, "#080910", 0.28);
  const accent = mixColors(palette.accent, "#09070A", 0.2);
  const cardStyle: CardCssProperties = {
    width: spec.canvas.width,
    height: spec.canvas.height,
    color: textColor,
    fontFamily: serif
      ? '"Source Han Serif SC", "Source Han Serif Heavy Local", serif'
      : '"Source Han Sans SC", "Source Han Sans Heavy Local", sans-serif',
    "--card-width": `${spec.canvas.width}px`,
    "--card-height": `${spec.canvas.height}px`,
    "--text-color": textColor,
    "--text-muted": withAlpha(textColor, 0.66),
    "--text-soft": withAlpha(textColor, 0.48),
    "--shadow-color": darkText ? "transparent" : "rgba(0, 0, 0, 0.34)",
    "--palette-primary": primary,
    "--palette-secondary": secondary,
    "--palette-accent": accent,
    "--palette-dominant": palette.dominant,
    "--grid-size": `${GRID_SIZES[spec.visual.gridDensity]}px`,
    "--grid-opacity": String(spec.visual.gridOpacity),
    "--lyric-size": `${lyricSize}px`,
    "--translation-size": `${Math.max(19, Math.round(lyricSize * spec.typography.translationScale))}px`,
    "--lyric-line-height": String(spec.typography.lineHeight),
    "--cover-scale": String(spec.media.coverCropScale)
  };

  return (
    <article
      className={`lyrics-card lyrics-card--${spec.canvas.layoutMode} background--${spec.visual.backgroundMode}`}
      style={cardStyle}
      data-export-card="true"
      data-layout-mode={spec.canvas.layoutMode}
      data-ratio={spec.canvas.ratio}
    >
      <CardBackground spec={spec} />
      {spec.canvas.layoutMode === "landscape" ? (
        <LandscapeContent
          spec={spec}
          lines={lines}
          translations={translations}
          textColor={textColor}
          source={source}
          showFooter={showFooter}
        />
      ) : (
        <PortraitContent
          spec={spec}
          lines={lines}
          translations={translations}
          textColor={textColor}
          source={source}
          showFooter={showFooter}
        />
      )}
    </article>
  );
}

function CardBackground({ spec }: { spec: RenderSpec }) {
  return (
    <div className="card-background" aria-hidden="true">
      <div className="background-base" />
      <div className="background-blob background-blob--one" />
      <div className="background-blob background-blob--two" />
      <div className="background-blob background-blob--three" />
      <svg className="background-ribbons" viewBox="0 0 1080 1350" preserveAspectRatio="none">
        <path d="M-60 236 C252 108 324 462 548 442 C784 420 806 108 1146 166 L1146 454 C822 382 746 702 510 694 C250 686 190 364 -60 518Z" />
        <path d="M1120 918 C842 792 724 1084 504 1048 C284 1012 202 744 -74 834 L-74 1102 C212 1018 328 1276 560 1252 C794 1228 888 982 1120 1168Z" />
      </svg>
      <div className="background-shade" />
      {spec.visual.gridEnabled ? <div className="background-grid" data-card-fine-grid="true" /> : null}
      <div className="background-sheen" />
    </div>
  );
}

function PortraitContent({
  spec,
  lines,
  translations,
  textColor,
  source,
  showFooter
}: CardContentProps) {
  const showHeader = spec.content.mode === "lyrics" && (spec.visibility.showCover || spec.visibility.showSongInfo);

  return (
    <div className="portrait-content" data-card-content="true">
      {showHeader ? (
        <header className="portrait-header" data-card-header="true">
          {spec.visibility.showCover ? <CoverArtwork spec={spec} /> : null}
          {spec.visibility.showSongInfo ? <SongInfo spec={spec} textColor={textColor} /> : null}
        </header>
      ) : null}
      <main className={`portrait-main mode--${spec.content.mode}`} data-card-lyrics-viewport="true">
        {spec.content.mode === "instrumental" ? (
          <InstrumentalBlock spec={spec} />
        ) : (
          <LyricsBlock spec={spec} lines={lines} translations={translations} />
        )}
      </main>
      {showFooter ? <CardFooter spec={spec} source={source} textColor={textColor} /> : null}
    </div>
  );
}

function LandscapeContent({
  spec,
  lines,
  translations,
  textColor,
  source,
  showFooter
}: CardContentProps) {
  const hasCover = spec.visibility.showCover && spec.content.mode === "lyrics";

  return (
    <div className={`landscape-content ${hasCover ? "has-cover" : "no-cover"}`} data-card-content="true">
      {hasCover ? (
        <div className="landscape-cover-slot">
          <CoverArtwork spec={spec} />
        </div>
      ) : null}
      <div className="landscape-copy">
        {spec.content.mode === "instrumental" ? (
          <InstrumentalBlock spec={spec} />
        ) : (
          <>
            {spec.visibility.showSongInfo ? <SongInfo spec={spec} textColor={textColor} landscape /> : null}
            <div className="landscape-lyrics-viewport" data-card-lyrics-viewport="true">
              <LyricsBlock spec={spec} lines={lines} translations={translations} />
            </div>
          </>
        )}
      </div>
      {showFooter ? <CardFooter spec={spec} source={source} textColor={textColor} landscape /> : null}
    </div>
  );
}

type CardContentProps = {
  spec: RenderSpec;
  lines: string[];
  translations: string[];
  textColor: string;
  source: SongSource;
  showFooter: boolean;
};

function CoverArtwork({ spec }: { spec: RenderSpec }) {
  const src = resolveCoverAssetUrl(spec.song.coverAssetId);
  const [failed, setFailed] = useState(false);

  useEffect(() => setFailed(false), [src]);

  return (
    <div className="cover-art" data-card-cover="true">
      {src && !failed ? (
        <img
          src={src}
          alt=""
          draggable={false}
          decoding="async"
          onError={() => setFailed(true)}
          style={{ transform: `scale(${spec.media.coverCropScale})` }}
        />
      ) : (
        <div className="cover-placeholder">
          <span className="cover-orbit cover-orbit--one" />
          <span className="cover-orbit cover-orbit--two" />
          <span className="cover-note" aria-hidden="true">♪</span>
          <span className="cover-caption">LYRICS</span>
        </div>
      )}
    </div>
  );
}

function SongInfo({ spec, textColor, landscape = false }: { spec: RenderSpec; textColor: string; landscape?: boolean }) {
  return (
    <div className={`song-info ${landscape ? "song-info--landscape" : ""}`} data-card-song-info="true">
      <h1 className={spec.typography.twoLineTitle ? "title-two-lines" : "title-one-line"}>
        <span>{spec.song.title || localeFallback(spec.locale, "title")}</span>
        {spec.song.explicit ? <ExplicitBadge color={textColor} /> : null}
      </h1>
      <p className="song-artist">{spec.song.artist || localeFallback(spec.locale, "artist")}</p>
      {spec.visibility.showAlbum && spec.song.album ? <p className="song-album">{spec.song.album}</p> : null}
    </div>
  );
}

function ExplicitBadge({ color }: { color: string }) {
  return (
    <span className="explicit-badge" aria-label="Explicit" style={{ borderColor: color }}>
      E
    </span>
  );
}

function LyricsBlock({ spec, lines, translations }: { spec: RenderSpec; lines: string[]; translations: string[] }) {
  const activeLines = lines.length > 0 ? lines : [localeFallback(spec.locale, "lyrics")];
  const hasTranslations = spec.content.translationEnabled && translations.length > 0;
  const alignment = alignmentClass(spec.typography.alignment);

  return (
    <div className={`lyrics-stack ${alignment}`} data-card-lyrics="true">
      {activeLines.map((line, index) => {
        const translation = hasTranslations ? translations[index] : "";
        return (
          <div className="lyric-pair" key={`${index}-${line}`}>
            <p className="lyric-line">{line || "\u00A0"}</p>
            {translation ? <p className="translation-line">{translation}</p> : null}
          </div>
        );
      })}
    </div>
  );
}

function InstrumentalBlock({ spec }: { spec: RenderSpec }) {
  return (
    <div className="instrumental-block" data-card-lyrics="true">
      {spec.visibility.showCover ? <CoverArtwork spec={spec} /> : <div className="instrumental-disc">♪</div>}
      <div>
        <p className="instrumental-kicker">INSTRUMENTAL</p>
        <p className="instrumental-title">{spec.content.instrumentalText || localeFallback(spec.locale, "instrumental")}</p>
        {spec.visibility.showSongInfo ? (
          <p className="instrumental-song">{[spec.song.title, spec.song.artist].filter(Boolean).join(" · ")}</p>
        ) : null}
      </div>
    </div>
  );
}

function CardFooter({
  spec,
  source,
  textColor,
  landscape = false
}: {
  spec: RenderSpec;
  source: SongSource;
  textColor: string;
  landscape?: boolean;
}) {
  const hasTopRow =
    (spec.visibility.showPlatformBadge && source !== "unknown") ||
    (spec.visibility.showSharedBy && spec.branding.sharedByName.trim().length > 0);

  return (
    <footer className={`card-footer ${landscape ? "card-footer--landscape" : ""}`} data-card-footer="true">
      {hasTopRow ? (
        <div className="footer-top-row">
          <div>{spec.visibility.showPlatformBadge && source !== "unknown" ? <PlatformMark source={source} /> : null}</div>
          <div className="shared-by" style={{ color: textColor }}>
            {spec.visibility.showSharedBy ? spec.branding.sharedByName.trim() : null}
          </div>
        </div>
      ) : null}
      {spec.visibility.showGeneratedWatermark ? (
        <div className="generated-watermark">
          <span />
          <p>generated by lyric card generator</p>
          <span />
        </div>
      ) : null}
    </footer>
  );
}

function PlatformMark({ source }: { source: Exclude<SongSource, "unknown"> | SongSource }) {
  return (
    <div className={`platform-mark platform-mark--${source}`} aria-label={platformName(source)}>
      <span className="platform-symbol">{platformSymbol(source)}</span>
      <span className="platform-name">{platformName(source)}</span>
    </div>
  );
}

function platformSymbol(source: SongSource) {
  switch (source) {
    case "spotify":
      return "≋";
    case "apple":
      return "♪";
    case "qq":
      return "Q";
    case "netease":
      return "◎";
    default:
      return "♪";
  }
}

function platformName(source: SongSource) {
  switch (source) {
    case "spotify":
      return "Spotify";
    case "apple":
      return "Apple Music";
    case "qq":
      return "QQ Music";
    case "netease":
      return "NetEase Music";
    default:
      return "Music";
  }
}

function fitLyricSize(
  spec: RenderSpec,
  lineCount: number,
  hasTranslation: boolean,
  showHeader: boolean,
  showFooter: boolean
) {
  const requested = spec.typography.lyricSize;
  const base = lineCount > 10 ? requested - 6 : requested;
  const height = spec.canvas.height;
  const width = spec.canvas.width;
  const verticalPadding = spec.canvas.layoutMode === "landscape"
    ? clamp(Math.round(Math.min(width, height) * 0.07), 48, 108)
    : clamp(Math.round(width * 0.062), 48, 84);
  const header = showHeader
    ? spec.canvas.layoutMode === "landscape"
      ? clamp(Math.round(height * 0.24), 150, 290)
      : clamp(Math.round(width * 0.205), 150, 280)
    : 0;
  const footer = showFooter ? clamp(Math.round(height * 0.09), 76, 128) : 0;
  const available = Math.max(200, height - verticalPadding * 2 - header - footer - (showHeader ? 42 : 0) - (showFooter ? 30 : 0));
  const translationHeight = hasTranslation ? base * spec.typography.translationScale * 1.3 : 0;
  const pairGap = base * (hasTranslation ? 0.38 : 0.2);
  const estimated = lineCount * (base * spec.typography.lineHeight + translationHeight + pairGap);
  if (estimated <= available) {
    return Math.max(30, base);
  }
  return clamp(Math.floor(base * (available / estimated)), 24, base);
}

function alignmentClass(alignment: TextAlignment) {
  return `align-${alignment}`;
}

function splitUsefulLines(text: string) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter((line, index, lines) => line.trim().length > 0 || (index > 0 && index < lines.length - 1));
}

function localeFallback(locale: RenderSpec["locale"], kind: "title" | "artist" | "lyrics" | "instrumental") {
  const chinese = locale === "zh" || locale === "zh-TW";
  if (kind === "title") return chinese ? "歌曲名称" : "Untitled";
  if (kind === "artist") return chinese ? "未知艺术家" : "Unknown artist";
  if (kind === "instrumental") return chinese ? "纯音乐" : "Instrumental";
  return chinese ? "在这里输入歌词…" : "Type your lyrics here…";
}

function clamp(value: number, minimum: number, maximum: number) {
  return Math.min(maximum, Math.max(minimum, value));
}
