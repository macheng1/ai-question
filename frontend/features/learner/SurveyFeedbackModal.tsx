"use client";
import { useEffect, useState } from "react";
import { Modal, Banner, Spin } from "@douyinfe/semi-ui";
import { getSurvey, type Training } from "@/features/trainings/api";
export function SurveyFeedbackModal({ id, onClose }: { id?: string; onClose: () => void }) {
  const [survey, setSurvey] = useState<Training>();
  const [surveyError, setSurveyError] = useState("");
  useEffect(() => {
    if (!id) return;
    let active = true;
    setSurvey(undefined); setSurveyError("");
    getSurvey(id).then(value => { if (active) setSurvey(value); })
      .catch(e => { if (active) setSurveyError(e.message || "加载评估失败"); });
    return () => { active = false; };
  }, [id]);
  return (      <Modal title="我的评估反馈" visible={Boolean(id)} onCancel={onClose} footer={null} centered width={640} style={{ maxWidth: "calc(100vw - 32px)" }} bodyStyle={{ maxHeight: "65vh", overflowY: "auto" }}>
        {surveyError ? <Banner type="danger" description={surveyError} /> : !survey ? <div className="py-8 text-center"><Spin /></div> : <div className="flex flex-col gap-4 pb-4">
          <p className="break-words font-medium">{survey.name}</p>
          {survey.questions.map((q, index) => <section key={q.id} className="rounded-lg bg-[var(--semi-color-fill-0)] p-4">
            <h3 className="mb-2 text-sm font-medium">{index + 1}. {q.title}</h3>
            <p className="whitespace-pre-wrap break-words text-sm leading-6">{survey.submittedAnswers?.[q.id]?.trim() ? `${survey.submittedAnswers[q.id]}${q.type === "RATING" ? " 分 / 5 分" : ""}` : "未填写"}</p>
          </section>)}
        </div>}
      </Modal>);
}
