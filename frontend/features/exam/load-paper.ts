import type { ExamPaper } from "./types";

type ApiQuestion = Omit<
  ExamPaper["questions"][number],
  "answer" | "analysis"
> & {
  answer?: string[];
  analysis?: string;
};

type ApiPaper = Omit<ExamPaper, "questions"> & {
  questions?: ApiQuestion[];
};

type ApiEnvelope<T> = {
  success: boolean;
  data: T;
  message?: string;
};

/** 读取公开考试试卷；所有题目必须来自后端真实试卷或答题快照。 */
export async function loadExamPaper(
  id: string,
  options: { revealAnswers?: boolean; attemptId?: number } = {},
): Promise<ExamPaper> {
  try {
    const params = new URLSearchParams();
    if (options.revealAnswers) params.set("revealAnswers", "true");
    if (options.attemptId) params.set("attemptId", String(options.attemptId));
    const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:18080/api";
    const response = await fetch(
      `${baseURL}/public/exams/${id}${params.toString() ? `?${params}` : ""}`,
      { cache: "no-store" },
    );
    const envelope = (await response.json()) as ApiEnvelope<ApiPaper>;
    if (!response.ok || !envelope.success || !envelope.data) {
      throw new Error(envelope.message || `加载考试失败（${response.status}）`);
    }
    const data = envelope.data;
    return {
      ...data,
      id,
      questions: (data.questions || []).map((item) => ({
        ...item,
        answer: item.answer || [],
        analysis: item.analysis || "",
      })),
    };
  } catch (error) {
    throw error;
  }
}
