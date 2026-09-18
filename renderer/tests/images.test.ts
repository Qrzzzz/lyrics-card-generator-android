import { afterEach, expect, it, vi } from 'vitest';
import { waitForCardImages, imageLayoutKey } from '../src/images';

function fixture() {
  const image = Object.assign(new EventTarget(), { complete: false, naturalWidth: 0, naturalHeight: 0,
    getAttribute: () => 'cover' });
  let attached = true;
  const node = { querySelectorAll: () => [image], contains: () => attached } as unknown as HTMLElement;
  return { image, node, detach: () => { attached = false; } };
}
afterEach(() => vi.useRealTimers());
it('waits for load and invalidates the resource key when dimensions arrive without decode()', async () => {
  const { image, node } = fixture();
  const before = imageLayoutKey(node);
  let ready = false;
  const wait = waitForCardImages(node).then(() => { ready = true; });
  await Promise.resolve();
  expect(ready).toBe(false);
  Object.assign(image, { complete: true, naturalWidth: 200, naturalHeight: 100 });
  image.dispatchEvent(new Event('load'));
  await wait;
  expect(imageLayoutKey(node)).not.toBe(before);
});
it('bounds the wait and removes old listeners after timeout', async () => {
  vi.useFakeTimers();
  const { image, node } = fixture();
  const error = vi.fn(); image.addEventListener('error', error);
  const wait = waitForCardImages(node, 50);
  await vi.advanceTimersByTimeAsync(50); await wait;
  expect(error).toHaveBeenCalledTimes(1);
  image.dispatchEvent(new Event('load'));
  expect(error).toHaveBeenCalledTimes(1);
  expect(vi.getTimerCount()).toBe(0);
});
it('does not deliver a stale timeout to a detached resource', async () => {
  vi.useFakeTimers();
  const { image, node, detach } = fixture();
  const error = vi.fn(); image.addEventListener('error', error);
  const wait = waitForCardImages(node, 50); detach();
  await vi.advanceTimersByTimeAsync(50); await wait;
  expect(error).not.toHaveBeenCalled();
});
it('settles an already failed image without waiting for another event', async () => {
  const { image, node } = fixture(); image.complete = true;
  await waitForCardImages(node);
});
