import { apiClient } from "@/lib/api/client";
export type LearnerTraining = {
  id: number;
  name: string;
  description: string;
  category: string;
  requirementType: string;
  purpose: string;
  assessmentId?: number;
  learningStatus: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";
  progress: number;
  studiedSeconds: number;
  totalUnits: number;
  completedUnits: number;
  assignedAt?: string;
  startedAt?: string;
  completedAt?: string;
  examName?: string;
  examStartAt?: string;
  examEndAt?: string;
  examStatus?: string;
  attemptId?: number;
  attemptCount: number;
  remainingAttempts?: number;
  canRetake?: boolean;
  score?: number | null;
  totalScore?: number;
  passed?: boolean | null;
  started: boolean;
  surveyEnabled: boolean;
  surveySubmitted: boolean;
};
export type LearningRecord = LearnerTraining & {
  trainingId: number;
  completed: boolean;
};
export type ExamRecord = {
  id: number;
  taskId: number;
  name: string;
  status: string;
  attemptNo: number;
  score: number | null;
  totalScore: number;
  passed: boolean | null;
  startedAt: string;
  submittedAt?: string;
  durationSeconds?: number;
};
export type Mistake = {
  id: number;
  examName: string;
  stem: string;
  userAnswer: string;
  correctAnswer: string;
  options: { optionKey: string; optionText: string }[];
  analysis: string;
};
export const getLearnerTrainings = () =>
  apiClient.get<LearnerTraining[]>("/learner/trainings");
export const getLearningRecords = () =>
  apiClient.get<LearningRecord[]>("/learner/records");
export const getExamRecords = () =>
  apiClient.get<ExamRecord[]>("/learner/exams");
export const getMistakes = () => apiClient.get<Mistake[]>("/learner/mistakes");

export type LearningMaterialRecord = {
  id: number;
  title: string;
  materialType: string;
  totalUnits: number;
  completedUnits: number;
  studiedSeconds: number;
  completedAt?: string;
};
export type LearnerTrainingDetail = {
  training: LearnerTraining;
  materials: LearningMaterialRecord[];
  attempts: ExamRecord[];
};
export const getLearnerTrainingDetail = (id: string) =>
  apiClient.get<LearnerTrainingDetail>(`/learner/trainings/${id}`);
