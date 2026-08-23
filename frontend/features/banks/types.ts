export type QuestionBank = { id:number; name:string; questionCount:number; source:string; category:string; published:boolean; description?:string };
export type PublicBankCategory = { id:number; name:string; parentId?:number|null; description?:string; sortNo:number };
export type PageResult<T> = { records:T[]; total:number; pageNo:number; pageSize:number; pages:number };
export type QuestionOption = { id?:number; questionId?:number; optionKey:string; optionText:string; correct?:boolean; sortNo?:number };
export type PublicQuestion = { id:number; bankId:number; questionType:string; stem:string; answerText?:string; analysis?:string; difficulty?:string; options?:QuestionOption[] };
