"use client";
import { useParams } from "next/navigation";
import { TrainingEditor } from "@/features/trainings/components/TrainingEditor";
export default function CopyTrainingPage() {
  const { id } = useParams<{ id: string }>();
  return <TrainingEditor key={id} copyFrom={id} />;
}
