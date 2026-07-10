import { useEffect, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { LyricsCard } from "./Card";
import { DEFAULT_RENDER_SPEC } from "./defaultSpec";
import { renderNodeAsPng, waitForStableRender } from "./export";
import { installRendererController } from "./runtime";
import type { RenderSpec } from "./types";

export function App() {
  const [spec, setSpec] = useState(DEFAULT_RENDER_SPEC);
  const cardRef = useRef<HTMLElement>(null);
  const viewport = useViewportSize();
  const scale = Math.min(
    Math.max(1, viewport.width - 20) / spec.canvas.width,
    Math.max(1, viewport.height - 20) / spec.canvas.height
  );

  useEffect(() => {
    async function applySpec(nextSpec: RenderSpec) {
      flushSync(() => setSpec(nextSpec));
      await waitForStableRender();
    }

    return installRendererController({
      applySpec,
      async measure(nextSpec) {
        await applySpec(nextSpec);
        const node = requireCardNode(cardRef.current);
        return {
          width: nextSpec.canvas.width,
          height: measureCardHeight(node, nextSpec)
        };
      },
      async exportPng(nextSpec, pixelRatio) {
        await applySpec(nextSpec);
        const node = requireCardNode(cardRef.current);
        return renderNodeAsPng(node, nextSpec.canvas.width, nextSpec.canvas.height, pixelRatio);
      }
    });
  }, []);

  return (
    <main className="renderer-shell" aria-label="Lyrics card preview">
      <div
        className="preview-scaler"
        style={{ transform: `translate(-50%, -50%) scale(${Math.max(0.01, scale)})` }}
      >
        <div ref={cardRef as React.RefObject<HTMLDivElement>}>
          <LyricsCard spec={spec} />
        </div>
      </div>
    </main>
  );
}

function useViewportSize() {
  const [size, setSize] = useState({ width: window.innerWidth, height: window.innerHeight });
  useEffect(() => {
    const update = () => setSize({ width: window.innerWidth, height: window.innerHeight });
    window.addEventListener("resize", update);
    return () => window.removeEventListener("resize", update);
  }, []);
  return size;
}

function requireCardNode(wrapper: HTMLElement | null) {
  const node = wrapper?.querySelector<HTMLElement>("[data-export-card='true']");
  if (!node) throw new Error("Export card node is unavailable");
  return node;
}

function measureCardHeight(node: HTMLElement, spec: RenderSpec) {
  if (!spec.canvas.autoHeight || spec.canvas.layoutMode !== "portrait" || spec.canvas.ratio !== "custom") {
    return spec.canvas.height;
  }

  const content = node.querySelector<HTMLElement>("[data-card-content='true']");
  const header = node.querySelector<HTMLElement>("[data-card-header='true']");
  const lyrics = node.querySelector<HTMLElement>("[data-card-lyrics='true']");
  const footer = node.querySelector<HTMLElement>("[data-card-footer='true']");
  if (!content || !lyrics) throw new Error("Measurement nodes are unavailable");

  const contentStyle = getComputedStyle(content);
  const main = node.querySelector<HTMLElement>("[data-card-lyrics-viewport='true']");
  const mainStyle = main ? getComputedStyle(main) : null;
  const padding = numberValue(contentStyle.paddingTop) + numberValue(contentStyle.paddingBottom);
  const mainPadding = mainStyle
    ? numberValue(mainStyle.paddingTop) + numberValue(mainStyle.paddingBottom)
    : 0;
  const headerHeight = header?.scrollHeight ?? 0;
  const lyricsHeight = lyrics.scrollHeight;
  const footerHeight = footer?.scrollHeight ?? 0;
  const gaps = (header ? 30 : 0) + (footer ? 24 : 0);
  const measured = Math.ceil(padding + mainPadding + headerHeight + lyricsHeight + footerHeight + gaps);
  return Math.min(3200, Math.max(720, measured));
}

function numberValue(value: string) {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}
