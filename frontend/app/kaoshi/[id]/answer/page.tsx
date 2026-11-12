import { ExamAttemptPage } from "@/features/exam/components/ExamAttemptPage";
export default async function Page({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ attemptId?: string }> }) {
  const { id } = await params;
  const { attemptId } = await searchParams;
  return <ExamAttemptPage id={id} attemptId={attemptId} mode="answer" />;
}
