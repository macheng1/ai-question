import { TrainingEditor } from "@/features/trainings/components/TrainingEditor";
export default async function NewTrainingPage({ searchParams }: { searchParams: Promise<{ paperId?: string }> }) {
  const { paperId } = await searchParams;
  const id = Number(paperId);
  return <TrainingEditor initialPaperId={Number.isSafeInteger(id) && id > 0 ? id : undefined} />;
}
