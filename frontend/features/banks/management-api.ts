import { apiClient } from "@/lib/api/client";
import type { PageResult, QuestionBank } from "./types";

export type SaveBank = { name:string; category:string; source?:string; published?:boolean };
export type Category = { id:number; name:string; parentId?:number|null; description?:string; sortNo:number };
export function getManagedBanks(keyword?:string){ return apiClient.get<PageResult<QuestionBank>>("/management/banks", { pageNo:1, pageSize:50, keyword }); }
export function createBank(data:SaveBank){ return apiClient.post<QuestionBank>("/management/banks", data); }
export function updateBank(id:number,data:SaveBank){ return apiClient.put<QuestionBank>(`/management/banks/${id}`, data); }
export function deleteBank(id:number){ return apiClient.delete<void>(`/management/banks/${id}`); }
export function getBankCategories(){ return apiClient.get<Category[]>("/management/bank-categories"); }
export function createBankCategory(data:Omit<Category,"id">){ return apiClient.post<Category>("/management/bank-categories", data); }
export function updateBankCategory(id:number,data:Omit<Category,"id">){ return apiClient.put<Category>(`/management/bank-categories/${id}`, data); }
export function deleteBankCategory(id:number){ return apiClient.delete<void>(`/management/bank-categories/${id}`); }
export type QuestionOption = { id?:number; optionKey:string; optionText:string; correct?:boolean; sortNo?:number };
export type QuestionItem = { id:number; bankId:number; questionType:string; stem:string; answerText?:string; analysis?:string; tags?:string; source?:string; difficulty?:string; score?:number; options?:QuestionOption[] };
export function getBankQuestions(bankId:number){ return apiClient.get<{records:QuestionItem[];total:number}>("/management/questions", { bankId, pageNo:1, pageSize:50 }); }
export type SaveQuestion = { bankId:number; questionType:string; stem:string; answerText?:string; analysis?:string; tags?:string; source?:string; difficulty?:string; score?:number; options?:QuestionOption[] };
export function getQuestion(id:number){ return apiClient.get<QuestionItem>(`/management/questions/${id}`); }
export function createQuestion(data:SaveQuestion){ return apiClient.post<QuestionItem>("/management/questions", data); }
export function updateQuestion(id:number,data:Omit<SaveQuestion,"bankId">){ return apiClient.put<QuestionItem>(`/management/questions/${id}`, data); }
export function deleteQuestion(id:number){ return apiClient.delete<void>(`/management/questions/${id}`); }
export type QuestionImportResult = { imported:number; skipped:number; errors:string[] };
export function importQuestions(bankId:number,file:File){ return apiClient.uploadForm<QuestionImportResult>("/management/questions/import", { bankId:String(bankId) }, "file", file); }
export function getQuestionImportTemplate(){ return apiClient.download("/management/questions/template"); }
