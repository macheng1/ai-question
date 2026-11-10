export function formatStudyHours(hours: number): string {
  if (hours > 0 && hours < 0.01) return "<0.01";
  return String(Number(hours.toFixed(2)));
}
