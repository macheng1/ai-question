type DisplayOption = { optionKey: string; optionText: string; correct?: boolean };

/** Only formats labels; never changes the option keys used for submitting answers. */
export function formatQuestionOption(option: DisplayOption): string {
  const key = option.optionKey.trim().toUpperCase();
  if (key === "TRUE") return "A. 正确";
  if (key === "FALSE") return "B. 错误";
  return `${option.optionKey}. ${option.optionText}`;
}

export function getQuestionDisplayOptions(question: {
  questionType: string;
  answerText?: string;
  options?: DisplayOption[];
}): DisplayOption[] {
  if (question.options?.length) return question.options;
  if (!["TRUE_FALSE", "JUDGE"].includes(question.questionType)) return [];
  const answer = question.answerText?.trim().toUpperCase();
  return [
    { optionKey: "TRUE", optionText: "正确", correct: ["TRUE", "A", "正确"].includes(answer ?? "") },
    { optionKey: "FALSE", optionText: "错误", correct: ["FALSE", "B", "错误"].includes(answer ?? "") },
  ];
}
