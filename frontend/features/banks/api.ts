import { apiClient } from "@/lib/api/client";
import type { PageResult, PublicBankCategory, PublicQuestion, QuestionBank } from "./types";

export function getQuestionBanks(params:{pageNo?:number;pageSize?:number;keyword?:string}={}){ return apiClient.get<PageResult<QuestionBank>>("/public/banks", { pageNo:params.pageNo??1, pageSize:params.pageSize??100, keyword:params.keyword }); }
export function getQuestionBank(id:number){ return apiClient.get<QuestionBank>(`/public/banks/${id}`); }
export function getPublicBankQuestions(id:number, params:{pageNo?:number;pageSize?:number;keyword?:string}={}){ return apiClient.get<PageResult<PublicQuestion>>(`/public/banks/${id}/questions`, { pageNo:params.pageNo??1, pageSize:params.pageSize??100, keyword:params.keyword }); }
export function getFavoriteBankIds(){ return apiClient.get<number[]>("/public/bank-favorites"); }
export function addFavoriteBank(id:number){ return apiClient.post<void>(`/public/bank-favorites/${id}`); }
export function removeFavoriteBank(id:number){ return apiClient.delete<void>(`/public/bank-favorites/${id}`); }
export function getPublicBankCategories(){ return apiClient.get<PublicBankCategory[]>("/public/bank-categories"); }
