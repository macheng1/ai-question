import { apiClient } from "@/lib/api/client";
import type { Material } from "./TrainingMaterials";
import type { DraftMaterial } from "./TrainingDraftMaterials";

/** Load references only. Source files are copied by the server after final confirmation. */
export async function loadCopyMaterials(sourceTrainingId: string): Promise<DraftMaterial[]> {
  const [course, materials] = await Promise.all([
    apiClient.get<{ videos: { id: number; title: string }[] }>(`/trainings/${sourceTrainingId}`),
    apiClient.get<Material[]>(`/management/trainings/${sourceTrainingId}/materials`),
  ]);
  return [
    ...course.videos.map(video => ({ file: new File([], video.title, { lastModified: video.id }), kind: "videos" as const, sourceId: video.id, sourceTrainingId })),
    ...materials.map(material => ({ file: new File([], material.title, { lastModified: material.id }), kind: "materials" as const,
      sourceId: material.id, sourceTrainingId, pageCount: material.pageCount,
      pages: material.pages.map(({ pageNumber, requiredSeconds }) => ({ pageNumber, requiredSeconds })),
    })),
  ];
}
