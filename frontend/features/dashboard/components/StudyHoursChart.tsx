import { Card, Empty, Pagination } from "@douyinfe/semi-ui";
import { studyAxisSeconds } from "../format";
import { formatStudyDuration } from "@/lib/duration";
import type { DashboardData } from "../types";

import { StatHint } from "./StatHint";

export function StudyHoursChart({ data, title, tip, totalHours, onPageChange, onTraining }: {
  data: DashboardData;
  title: string;
  totalHours: number;
  tip: string;
  onPageChange: (page: number) => void;
  onTraining?: (id: number) => void;
}) {
  const maximum = studyAxisSeconds(totalHours);
  return (
    <Card title={<span className="inline-flex items-center gap-1">{title}<StatHint label={title} text={`${tip} 条形表示累计有效时长，不是完成进度。左右图按全部数据的总学时统一刻度，翻页不改变刻度。`} /></span>}>
      {data.rows.length === 0 ? <Empty description="暂无符合条件的数据" /> : (
        <div>
        <p className="mb-3 text-xs text-slate-500">累计有效时长 · 非完成率</p>
        <div className="max-h-96 overflow-y-auto">
        <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-3 sm:grid-cols-[minmax(120px,30%)_minmax(0,1fr)_112px]">
          <div aria-hidden="true" className="relative col-span-2 h-5 text-xs tabular-nums text-slate-500 sm:col-span-1 sm:col-start-2">
            {[0, 1, 2, 3, 4].map(tick => (
              <span key={tick} className={`absolute whitespace-nowrap ${tick === 0 ? "" : tick === 4 ? "-translate-x-full" : "-translate-x-1/2"}`} style={{ left: `${tick * 25}%` }}>
                {formatStudyDuration(maximum * tick / 4 / 3600)}
              </span>
            ))}
          </div>
        <ul aria-label={title} className="col-span-full m-0 grid list-none grid-cols-subgrid gap-x-3 gap-y-3 p-0">
          {data.rows.map(row => (
            <li key={row.id} className="col-span-full grid grid-cols-subgrid items-center gap-y-1 text-sm">
              {onTraining && row.id != null ? (
                <button type="button" className="break-words text-left text-teal-700 hover:underline" onClick={() => onTraining(row.id!)}>{row.name}</button>
              ) : <span className="break-words">{row.name}</span>}
              <div aria-hidden="true" className="col-span-2 row-start-2 relative h-5 sm:col-span-1 sm:col-start-2 sm:row-start-1">
                {[0, 1, 2, 3, 4].map(tick => <span key={tick} className="absolute inset-y-0 border-l border-slate-200" style={{ left: `${tick * 25}%` }} />)}
                <div className="relative h-full rounded-r-sm bg-[#28856c]" style={{ width: `${maximum ? Math.min(100, Math.max(0, row.studiedHours * 3600 / maximum * 100)) : 0}%` }} />
              </div>
              <span className="col-start-2 row-start-1 whitespace-nowrap tabular-nums sm:col-start-3">{formatStudyDuration(row.studiedHours)}</span>
            </li>
          ))}
        </ul>
        </div>
        </div>
        </div>
      )}
      {data.total > data.pageSize && <Pagination className="mt-4" size="small" total={data.total} currentPage={data.page} pageSize={data.pageSize} onPageChange={onPageChange} />}
    </Card>
  );
}
