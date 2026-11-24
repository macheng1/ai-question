/** Display elapsed time without rounding short sessions away. */
export function formatDuration(value?: number | null): string {
  if (value == null || !Number.isFinite(value) || value < 0) return "—";
  if (value === 0) return "0秒";
  const seconds = Math.floor(value + 1e-7);
  if (seconds === 0) return "不足1秒";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor(seconds % 3600 / 60);
  const s = seconds % 60;
  return `${h ? `${h}小时` : ""}${m ? `${m}分` : ""}${s ? `${s}秒` : ""}`;
}

export function formatStudyDuration(hours: number): string {
  return formatDuration(hours * 3600);
}

/** Timestamp differences use their offsets; missing dates are not zero duration. */
export function formatElapsedDuration(start?: string | null, end?: string | null): string {
  if (!start || !end) return "—";
  const milliseconds = Date.parse(end) - Date.parse(start);
  if (!Number.isFinite(milliseconds)) return "—";
  // Match the API's whole-second exam durations (TIMESTAMPDIFF).
  return formatDuration(Math.max(0, Math.floor(milliseconds / 1000)));
}
