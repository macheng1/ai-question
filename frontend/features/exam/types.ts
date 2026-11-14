export type QuestionKind = "single" | "multi" | "judge" | "short";
export type ExamOption = { key: string; text: string };
export type ExamQuestion = {
  id: number;
  kind: QuestionKind;
  title: string;
  options: ExamOption[];
  answer: string[];
  analysis: string;
  score?: number;
};
export type ExamPaper = {
  id: string;
  title: string;
  subtitle: string;
  duration: number;
  passScore: number;
  totalScore: number;
  startAt?: string;
  endAt?: string;
  taskStatus?: string;
  trainingStartAt?: string;
  trainingEndAt?: string;
  questionCount?: number;
  reviewEnabled?: boolean;
  questions: ExamQuestion[];
};


export const questionLabels: Record<QuestionKind, string> = {
  single: "单选题", multi: "多选题", judge: "判断题", short: "问答题",
};

export function readSavedAnswer(kind: QuestionKind | undefined, value?: string): string[] {
  if (!value) return [];
  if (kind === "short") return [value];
  const values = value.split(",").filter(Boolean);
  if (kind !== "judge") return values;
  return values.map(item => {
    const key = item.trim().toUpperCase();
    return key === "TRUE" ? "A" : key === "FALSE" ? "B" : item;
  });
}

export function isAnswered(values?: string[]) {
  return Boolean(values?.some(value => value.trim().length > 0));
}
