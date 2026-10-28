"use client";

import { IconClock, IconClose, IconArrowRight, IconArrowLeft, IconTick, IconMenu, IconBookStroked, IconFile, IconStar, IconSearch, IconUser, IconSetting, IconGridView, IconBarChartVStroked, IconComment, IconFlag, IconApps, IconLayers } from "@douyinfe/semi-icons";

const icons = { clock: IconClock, close: IconClose, arrowRight: IconArrowRight, arrowLeft: IconArrowLeft, tick: IconTick, menu: IconMenu, book: IconBookStroked, file: IconFile, star: IconStar, search: IconSearch, user: IconUser, setting: IconSetting, grid: IconGridView, chart: IconBarChartVStroked, comment: IconComment, flag: IconFlag, apps: IconApps, layers: IconLayers };
export function AppIcon({ name, className }: { name: string; className?: string }) {
  const Icon = icons[name as keyof typeof icons] || IconFile;
  return <Icon className={className} style={{ fontSize: "inherit", verticalAlign: "middle" }} aria-hidden />;
}
