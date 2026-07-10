export const PROTOCOL_VERSION = 1 as const;
export const RENDERER_VERSION = "android-alpha-renderer-1" as const;

export type RenderLocale = "zh" | "zh-TW" | "en" | "fr" | "ja" | "es";
export type SongSource = "qq" | "netease" | "apple" | "spotify" | "unknown";
export type ContentMode = "lyrics" | "instrumental";
export type LayoutMode = "portrait" | "landscape";
export type CanvasRatio = "1:1" | "4:5" | "9:16" | "16:9" | "21:9" | "3:2" | "custom";
export type FontScheme = "sans-heavy" | "serif-heavy" | "system-sans" | "system-serif";
export type TextAlignment = "left" | "center" | "right";
export type TextColorMode = "auto" | "preset" | "custom";
export type TextColorPreset = "white" | "black" | "warmWhite" | "cream" | "charcoal" | "softBlue" | "softGold";
export type BackgroundMode = "palette" | "gradient";
export type GridDensity = "sparse" | "medium" | "dense";

export interface RenderSpec {
  schemaVersion: 1;
  rendererVersion: typeof RENDERER_VERSION;
  locale: RenderLocale;
  song: {
    source: SongSource;
    title: string;
    artist: string;
    album: string;
    explicit: boolean;
    coverAssetId: string | null;
  };
  content: {
    mode: ContentMode;
    lyrics: string;
    translationEnabled: boolean;
    translation: string;
    instrumentalText: string;
  };
  canvas: {
    layoutMode: LayoutMode;
    ratio: CanvasRatio;
    width: number;
    height: number;
    autoHeight: boolean;
    pixelRatio: 1 | 2;
  };
  typography: {
    fontScheme: FontScheme;
    fontFamily: string;
    lyricSize: number;
    lineHeight: number;
    alignment: TextAlignment;
    translationScale: number;
    twoLineTitle: boolean;
    textColorMode: TextColorMode;
    textColorPreset: TextColorPreset;
    customTextColor: string | null;
  };
  visual: {
    backgroundMode: BackgroundMode;
    palette: {
      dominant: string;
      secondary: string;
      accent: string;
    };
    gridEnabled: boolean;
    gridDensity: GridDensity;
    gridOpacity: number;
  };
  visibility: {
    showCover: boolean;
    showSongInfo: boolean;
    showAlbum: boolean;
    showPlatformBadge: boolean;
    showSharedBy: boolean;
    showGeneratedWatermark: boolean;
  };
  branding: {
    sharedByName: string;
    platform: SongSource;
  };
  media: {
    coverCropScale: number;
  };
}

export type HostMessageType =
  | "initialize"
  | "setSpec"
  | "measure"
  | "extractPalette"
  | "exportPng"
  | "cancel"
  | "ping";
export type RendererMessageType =
  | "ready"
  | "specApplied"
  | "measured"
  | "paletteExtracted"
  | "exportStarted"
  | "exportProgress"
  | "exportChunk"
  | "exportCompleted"
  | "renderError"
  | "log"
  | "pong";

export interface ProtocolEnvelope<TType extends string = string, TPayload = unknown> {
  protocolVersion: typeof PROTOCOL_VERSION;
  requestId: string;
  type: TType;
  payload: TPayload;
}

export type HostEnvelope = ProtocolEnvelope<HostMessageType, unknown>;
export type RendererEnvelope = ProtocolEnvelope<RendererMessageType, unknown>;

export type RendererErrorCode =
  | "RENDERER_NOT_READY"
  | "UNSUPPORTED_PROTOCOL"
  | "INVALID_SPEC"
  | "ASSET_NOT_FOUND"
  | "IMAGE_DECODE_FAILED"
  | "FONT_LOAD_TIMEOUT"
  | "MEASURE_FAILED"
  | "EXPORT_FAILED"
  | "EXPORT_OUT_OF_MEMORY"
  | "EXPORT_CANCELLED";

export interface RendererController {
  applySpec(spec: RenderSpec): Promise<void>;
  measure(spec: RenderSpec): Promise<{ width: number; height: number }>;
  exportPng(spec: RenderSpec, pixelRatio: 1 | 2): Promise<Blob>;
}

export interface LyricsCardRendererGlobal {
  receive(message: unknown): void;
}

declare global {
  interface Window {
    LyricsCardNative?: {
      postMessage(message: string): void;
    };
    LyricsCardRenderer: LyricsCardRendererGlobal;
  }
}
