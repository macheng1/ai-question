/** Format display values without changing the source statistics. */
export function formatStatistic(value: unknown): string {
  const number = Number(value);
  if (!Number.isFinite(number)) return "—";
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2, useGrouping: false }).format(number === 0 ? 0 : number);
}
export function formatPercentage(value: unknown): string {
  const text = formatStatistic(value);
  return text === "—" ? text : `${text}%`;
}
