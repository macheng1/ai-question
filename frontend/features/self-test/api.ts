import { apiClient } from "@/lib/api/client";
import type { PageResult } from "@/features/banks/types";

export type PublicSelfTest = {
  id: number;
  bankId: number;
  name: string;
  description?: string;
  singleCount: number;
  multipleCount: number;
  trueFalseCount: number;
  questionCount: number;
  bankName: string;
};

export type PublicSelfTestDetail = PublicSelfTest & {
  randomOrder: boolean;
  category?: string;
  source?: string;
};

export function getPublicSelfTests(params: { pageNo?: number; pageSize?: number; keyword?: string } = {}) {
  return apiClient.get<PageResult<PublicSelfTest>>("/public/self-tests", {
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 12,
    keyword: params.keyword || undefined,
  });
}

export function getPublicSelfTest(id: number) {
  return apiClient.get<PublicSelfTestDetail>(`/public/self-tests/${id}`);
}
