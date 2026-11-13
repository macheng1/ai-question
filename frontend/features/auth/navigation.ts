import type { AccountType } from "./api";

export const accountMenus = {
  ADMIN: [
    { label: "概览", href: "/profile/dashboard" },
    { label: "培训管理", href: "/profile/trainings" },
    { label: "题库管理", href: "/profile/banks" },
    { label: "试卷管理", href: "/profile/papers" },
    { label: "学员管理", href: "/profile/organization" },
  ],
  EMPLOYEE: [
    { label: "我的培训", href: "/learner" },
    { label: "培训记录", href: "/learner/records" },
    { label: "我的考试", href: "/learner/exams" },
    { label: "我的错题", href: "/learner/mistakes" },
    { label: "满意度评估", href: "/learner/surveys" },
  ],
} satisfies Record<AccountType, { label: string; href: string }[]>;

export const accountHome = (type: AccountType) =>
  type === "EMPLOYEE" ? "/learner" : "/profile/dashboard";
export const accountSettings = (type: AccountType) =>
  type === "EMPLOYEE" ? "/learner/settings" : "/profile/settings";
