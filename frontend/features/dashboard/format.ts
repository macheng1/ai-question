/** A shared axis based on the unpaginated total remains stable when paging. */
export function studyAxisSeconds(totalHours: number): number {
  const seconds = Number.isFinite(totalHours) ? Math.max(0, totalHours * 3600) : 0;
  const steps = [15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 18000, 36000];
  return (steps.find(step => step * 4 >= seconds) ?? Math.ceil(seconds / 14400) * 3600) * 4;
}
