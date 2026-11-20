"use client";
import { useEffect, useState } from "react";

/** Match the workspace mobile breakpoint without reading window during rendering. */
export function useCompactViewport(maxWidth = 767) {
  const [compact, setCompact] = useState(false);
  useEffect(() => {
    const query = window.matchMedia(`(max-width: ${maxWidth}px)`);
    const update = () => setCompact(query.matches);
    update();
    // addListener also supports older mobile Safari versions.
    query.addListener(update);
    return () => query.removeListener(update);
  }, [maxWidth]);
  return compact;
}
