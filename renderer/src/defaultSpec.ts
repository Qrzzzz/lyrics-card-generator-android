import { RENDERER_VERSION, type RenderSpec } from "./types";

export const DEFAULT_RENDER_SPEC: RenderSpec = {
  schemaVersion: 1,
  rendererVersion: RENDERER_VERSION,
  locale: "zh",
  song: {
    source: "netease",
    title: "在时间的风里",
    artist: "Lyrics Card",
    album: "Android Alpha",
    explicit: false,
    coverAssetId: null
  },
  content: {
    mode: "lyrics",
    lyrics: "晚风把城市写成一封信\n我们在时间的褶皱里相遇\n让每一句歌，都有光的形状",
    translationEnabled: true,
    translation: "The evening breeze writes the city into a letter\nWe meet in the folds of time\nLet every lyric take the shape of light",
    instrumentalText: "纯音乐"
  },
  canvas: {
    layoutMode: "portrait",
    ratio: "4:5",
    width: 1080,
    height: 1350,
    autoHeight: false,
    pixelRatio: 2
  },
  typography: {
    fontScheme: "sans-heavy",
    fontFamily: "Source Han Sans SC",
    lyricSize: 56,
    lineHeight: 1.4,
    alignment: "left",
    translationScale: 0.72,
    twoLineTitle: false,
    textColorMode: "auto",
    textColorPreset: "white",
    customTextColor: null
  },
  visual: {
    backgroundMode: "palette",
    palette: {
      dominant: "#173A5E",
      secondary: "#256A7B",
      accent: "#D98C63"
    },
    gridEnabled: true,
    gridDensity: "medium",
    gridOpacity: 0.12
  },
  visibility: {
    showCover: true,
    showSongInfo: true,
    showAlbum: true,
    showPlatformBadge: true,
    showSharedBy: true,
    showGeneratedWatermark: true
  },
  branding: {
    sharedByName: "Shared by Qrzzzz",
    platform: "netease"
  },
  media: {
    coverCropScale: 1
  }
};
