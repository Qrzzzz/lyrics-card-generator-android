// load/error work on the oldest supported WebView; decode() is not required.
export const IMAGE_TIMEOUT_MS = 4_000;

export function imageLayoutKey(node: HTMLElement) {
  return JSON.stringify(Array.from(node.querySelectorAll<HTMLImageElement>('.cover-art img'),
    image => [image.getAttribute('src'), image.complete, image.naturalWidth, image.naturalHeight]));
}

export async function waitForCardImages(node: HTMLElement, timeoutMs = IMAGE_TIMEOUT_MS) {
  await Promise.all(Array.from(node.querySelectorAll<HTMLImageElement>('.cover-art img'), image =>
    new Promise<void>(resolve => {
      const src = image.getAttribute('src');
      let done = false;
      let timer: ReturnType<typeof setTimeout> | undefined;
      const finish = (failed: boolean) => {
        if (done) return;
        done = true;
        clearTimeout(timer);
        image.removeEventListener('load', loaded);
        image.removeEventListener('error', errored);
        // Never deliver a timeout/error to a replacement resource or detached card.
        if (failed && node.contains(image) && image.getAttribute('src') === src) {
          image.dispatchEvent(new Event('error'));
        }
        resolve();
      };
      const loaded = () => finish(!(image.naturalWidth > 0 && image.naturalHeight > 0));
      const errored = () => finish(true);
      image.addEventListener('load', loaded);
      image.addEventListener('error', errored);
      timer = setTimeout(() => finish(true), timeoutMs);
      if (image.complete) loaded();
    })
  ));
}
