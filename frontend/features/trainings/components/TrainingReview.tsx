"use client";
import { useEffect, useState } from "react";
import { Banner, Spin } from "@douyinfe/semi-ui";
import { apiClient } from "@/lib/api/client";
import { CourseWorkspace } from "./CourseWorkspace";
import type { ExamLearning } from "./ExamTrainingVideos";
import type { Material } from "./TrainingMaterials";

type Course = ExamLearning["trainings"][number];
/** Read-only metadata uses the same workspace without opening a learning session. */
export function TrainingReview({ id, course, onBack }: { id: string; course: Omit<Course, "id">; onBack: () => void }) {
  const [data, setData] = useState<ExamLearning>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setData(undefined); setError("");
    apiClient.get<{ videos: { id: number; title: string }[]; materials: Omit<Material, "pages">[] }>(`/learner/trainings/${id}/review`, undefined, { signal: controller.signal })
      .then(result => {
        if (controller.signal.aborted) return;
        setData({ token: "", trainings: [{ ...course, id: Number(id) }],
          videos: result.videos.map(video => ({ ...video, trainingId: Number(id), positionSeconds: 0, completed: false })),
          materials: result.materials.map(material => ({ ...material, trainingId: Number(id), pages: Array.from({ length: material.pageCount }, (_, i) => ({ pageNumber: i + 1, requiredSeconds: 0 })) })),
        });
      }).catch(e => { if (!controller.signal.aborted) setError(e.message || "材料加载失败"); });
    return () => controller.abort();
  }, [id, course]);
  if (error) return <Banner type="danger" description={error} />;
  if (!data) return <Spin />;
  return <CourseWorkspace key={id} id={id} data={data} busy={false} reviewOnly onBack={onBack} onComplete={() => undefined} />;
}
