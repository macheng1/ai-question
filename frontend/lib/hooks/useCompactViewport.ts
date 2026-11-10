"use client";
import { useEffect, useState } from "react";

/** Match the workspace mobile breakpoint without reading window during rendering. */
export function useCompactViewport() {
  const [compact, setCompact] = useState(false);
  useEffect(() => {
    const query = window.matchMedia("(max-width: 767px)");
    const update = () => setCompact(query.matches);
    update();
    // addListener also supports older mobile Safari versions.
    query.addListener(update);
    return () => query.removeListener(update);
  }, []);
  return compact;
}
