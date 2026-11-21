import { useEffect, useRef } from 'react';
import { POLL_INTERVAL_MS } from '../api/client';

/**
 * Runs `fn` now and then every `intervalMs` while `enabled`.
 * The next run is scheduled only after the previous one settles, so slow responses never pile up,
 * and polling pauses while the tab is hidden. `fn` receives `true` for background (polled) runs.
 */
export function usePolling(
  fn: (isBackground: boolean) => void | Promise<void>,
  intervalMs: number = POLL_INTERVAL_MS,
  enabled = true
): void {
  const latest = useRef(fn);
  useEffect(() => {
    latest.current = fn;
  }, [fn]);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const schedule = () => {
      if (!cancelled && enabled) timer = setTimeout(() => void run(true), intervalMs);
    };
    const run = async (isBackground: boolean) => {
      if (isBackground && document.hidden) {
        schedule();
        return;
      }
      try {
        await latest.current(isBackground);
      } finally {
        schedule();
      }
    };

    void run(false);
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [intervalMs, enabled]);
}
