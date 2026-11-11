import Image from "next/image";
import { H5Shell } from "@/features/exam/components/H5Shell";
import { ExamRunner } from "@/features/exam/components/ExamRunner";
import { loadExamPaper } from "@/features/exam/load-paper";

export default async function ExamPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const paper = await loadExamPaper(id);
  const dateLabel = (date?: string) => date ? new Date(date).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai", hour12: false }) : "不限";
  return <H5Shell title="培训考试"><div className="h5-content h5-entry-layout"><section className="h5-entry-overview">
    <div className="h5-hero"><span className="h5-eyebrow">e-learning · 员工培训</span><Image src="/images/training/training-cover.svg" width={124} height={124} alt="" /><h1 className="h5-title">{paper.title}</h1>{paper.subtitle && <p>{paper.subtitle}</p>}</div>
    <div className="h5-stats"><div><strong>{paper.questionCount ?? paper.questions.length}<small>题</small></strong><span>题目数量</span></div><div><strong>{paper.duration}<small>分钟</small></strong><span>考试限时</span></div><div><strong>{paper.passScore}<small>/ {paper.totalScore}</small></strong><span>及格 / 总分</span></div></div>
    <p className="h5-muted">考试时间：{dateLabel(paper.startAt)} 至 {dateLabel(paper.endAt)}</p>
    <div className="h5-steps"><b>01 核对信息</b><i /><span>02 学习（如有）</span><i /><span>03 考试</span></div>
    </section><section className="h5-entry-identity"><ExamRunner paper={paper} compact allowRestore={false} /></section>
  </div></H5Shell>;
}
