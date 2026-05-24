import { useEffect, useRef, useState } from 'react';

/**
 * Measure a DOM element's content box via ResizeObserver. Returns a ref to
 * attach + the live { width, height } in px. Mirrors the observer pattern in
 * components/map/MapCanvas.tsx. The adaptive home attaches this to its own root
 * so it re-fits to its real content area (which changes with the window/chrome).
 */
export function useContainerSize() {
  const ref = useRef<HTMLDivElement>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      const r = entries[0]?.contentRect;
      if (r) setSize({ width: r.width, height: r.height });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  return [ref, size] as const;
}
