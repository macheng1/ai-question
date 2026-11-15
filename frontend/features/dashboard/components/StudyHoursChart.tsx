import { Card, Empty, Pagination } from "@douyinfe/semi-ui";
import { formatStudyHours } from "../format";
import type { DashboardData } from "../types";

import { StatHint } from "./StatHint";

export function StudyHoursChart({ data, title, tip, onPageChange, onTraining }: {
  data: DashboardData;
  title: string;
  tip: string;
  onPageChange: (page: number) => void;
  onTraining?: (id: number) => void;
}) {
  const maximum = Math.max(0, ...data.rows.map(row => row.studiedHours));
  return (
    <Card title={<span className="inline-flex items-center gap-1">{title}<StatHint label={title} text={`${tip} 条形长度按本页最长学时等比例显示，极短的非零时长以细线标记。`} /></span>}>
      {data.rows.length === 0 ? <Empty description="暂无符合条件的数据" /> : (
        <ul aria-label={title} className="m-0 grid max-h-96 list-none grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-3 overflow-y-auto p-0 pr-2 sm:grid-cols-[minmax(120px,30%)_minmax(0,1fr)_auto]">
          {data.rows.map(row => (
            <li key={row.id} className="col-span-full grid grid-cols-subgrid items-center gap-y-1 text-sm">
              {onTraining && row.id != null ? (
                <button type="button" className="break-words text-left text-teal-700 hover:underline" onClick={() => onTraining(row.id!)}>{row.name}</button>
              ) : <span className="break-words">{row.name}</span>}
              <div aria-hidden="true" className="col-span-2 row-start-2 h-3 overflow-hidden rounded-sm bg-slate-100 sm:col-span-1 sm:col-start-2 sm:row-start-1">
                <div className="h-full rounded-sm bg-[#28856c]" style={{ width: `${maximum ? row.studiedHours / maximum * 100 : 0}%`, minWidth: row.studiedHours > 0 ? 2 : 0 }} />
              </div>
              <span className="col-start-2 row-start-1 whitespace-nowrap tabular-nums sm:col-start-3">{formatStudyHours(row.studiedHours)} 小时</span>
            </li>
          ))}
        </ul>
      )}
      {data.total > data.pageSize && <Pagination className="mt-4" size="small" total={data.total} currentPage={data.page} pageSize={data.pageSize} onPageChange={onPageChange} />}
    </Card>
  );
}
