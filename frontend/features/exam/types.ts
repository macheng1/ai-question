export type QuestionKind = "single" | "multi" | "judge";
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
