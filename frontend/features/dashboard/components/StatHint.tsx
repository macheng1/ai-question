import { IconHelpCircle } from "@douyinfe/semi-icons";
import { Tooltip } from "@douyinfe/semi-ui";

export function StatHint({ label, text }: { label: string; text: string }) {
  return (
    <Tooltip trigger="click" content={text}>
      <button type="button" aria-label={label + "统计说明"}
        className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-slate-500 hover:text-teal-700">
        <IconHelpCircle size="small" />
      </button>
    </Tooltip>
  );
}
