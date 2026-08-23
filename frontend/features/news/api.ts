import { apiClient } from "@/lib/api/client";

export type NewsArticle = {
  id: number;
  title: string;
  summary?: string;
  content: string;
  publishedAt: string;
};

export function getNews() {
  return apiClient.get<NewsArticle[]>("/public/news");
}

export function getNewsArticle(id: number) {
  return apiClient.get<NewsArticle>(`/public/news/${id}`);
}
