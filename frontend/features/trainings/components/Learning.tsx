"use client";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Banner, Button, Card, Form, Image, Layout, Spin, Tag, Tooltip } from "@douyinfe/semi-ui";
import { IconArrowLeft, IconTickCircle, IconHelpCircleStroked, IconPlay, IconFile, IconImage } from "@douyinfe/semi-icons";
import { apiClient } from "@/lib/api/client";
import { CourseWorkspace } from "./CourseWorkspace";
import { loadExamLearning, type ExamLearning } from "./ExamTrainingVideos";
import { trainingCategories } from "../api";
import "./learning.css";
import { useLearnerStore } from "@/features/auth/store";
import { TrainingReview } from "./TrainingReview";
type Course = { assessmentId?: number; name: string; description: string; category: string; requirementType: string; surveyEnabled: boolean };
export function Learning({ id }: { id: string }) {
  const router = useRouter();
  const search = useSearchParams();
  const mode = search.get("review") === "1" ? "review" : search.get("exam") === "1" ? "exam" : search.get("resume") === "1" ? "resume" : "entry";
  const [course, setCourse] = useState<Course>();
  const [learning, setLearning] = useState<ExamLearning>();
  const [identity, setIdentity] = useState({ name: "", phone: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [reviewOnly, setReviewOnly] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setCourse(undefined);
    setLearning(undefined);
    setDone(false);
    setReviewOnly(false);
    setError("");
    apiClient.get<Course>(`/public/trainings/${id}/exam`, undefined, { signal: controller.signal }).then(data => {
      if (controller.signal.aborted) return;
      if (mode === "review") {
        setReviewOnly(true);
        setCourse(data);
      } else if (mode === "exam") {
        const person = useLearnerStore.getState().user;
        if (!person?.name || !person.phone) { setError("请先登录后参加考试"); return; }
        if (!data.assessmentId) { setError("本培训未安排考试"); return; }
        router.replace(`/kaoshi/${data.assessmentId}?trainingId=${id}&direct=1`);
      } else if (mode === "resume") {
        const person = useLearnerStore.getState().user;
        if (!person?.name || !person.phone) { setError("请先登录后继续学习"); return; }
        if (data.assessmentId) {
          const query = new URLSearchParams({ trainingId: id, name: person.name, phone: person.phone, fromLearner: "1" });
          router.replace(`/kaoshi/${data.assessmentId}/learning?${query}`);
        } else {
          setIdentity({ name: person.name, phone: person.phone });
          loadExamLearning(String(-Number(id)), person.name, person.phone)
            .then(result => { if (!controller.signal.aborted) { setLearning(result); setCourse(data); } })
            .catch(e => { if (!controller.signal.aborted) setError(e.message || "材料加载失败"); });
        }
      } else if (data.assessmentId) router.replace(`/kaoshi/${data.assessmentId}?trainingId=${id}`); else setCourse(data);
    }).catch(e => { if (!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [id, router, mode]);
  const enter = async (person: typeof identity) => {
    setBusy(true); setError("");
    try { setLearning(await loadExamLearning(String(-Number(id)), person.name, person.phone)); setIdentity(person); setDone(false); }
    catch (e) { setError(e instanceof Error ? e.message : "进入学习失败"); }
    finally { setBusy(false); }
  };
  const backToTraining = () => router.push(`/learner/trainings/${id}`);
  return <Layout className="course-learning min-h-screen"><Layout.Header className="border-b border-slate-200 bg-white"><div className="mx-auto flex min-h-16 max-w-[1280px] items-center justify-between gap-3 px-4 sm:px-6"><Button theme="borderless" type="tertiary" icon={<IconArrowLeft />} onClick={backToTraining}>返回</Button><span className="font-semibold">培训学习</span><span className="flex items-center gap-2"><Image src="/casela-logo.png" alt="CASELA Technologies" width={113} height={30} preview={false} /><span className="hidden text-sm sm:inline">e-learning</span></span></div></Layout.Header>
    <Layout.Content className="mx-auto w-full max-w-[1280px] px-4 py-6 sm:px-6 sm:py-10">
      {error && <Banner className="!mb-5" type="danger" description={error} />}
      {!course ? <div className="flex min-h-64 flex-col items-center justify-center gap-3">{!error && <><Spin size="large" /><p className="text-sm text-slate-500">正在加载培训…</p></>}</div> : reviewOnly ? <TrainingReview id={id} course={course} onBack={backToTraining} /> : done ? <Card className="!mx-auto max-w-xl"><div className="flex flex-col items-center gap-6 px-2 py-8 text-center sm:px-8"><IconTickCircle className="text-emerald-700" size="extra-large" /><div><h1 className="text-2xl font-semibold">培训学习已完成</h1><p className="mt-3 text-slate-500">你已完成本次培训的全部材料</p></div><div className="w-full rounded-lg bg-slate-50 p-5"><strong>{course.name}</strong><p className="mt-3 text-sm text-slate-500">全部材料已完成 · 进度已保存</p></div>{course.surveyEnabled && <Button block size="large" theme="solid" onClick={() => router.push(`/training/${id}/survey`)}>填写满意度评估</Button>}<Button block size="large" loading={busy} onClick={() => router.push(`/training/${id}?review=1`)}>回看培训材料</Button><Button block size="large" onClick={() => router.push("/learner")}>返回个人中心</Button></div></Card> : learning ? <CourseWorkspace id={id} data={learning} busy={busy} onBack={backToTraining} onComplete={async () => { setBusy(true); setError(""); try { await apiClient.post(`/public/courses/${id}/complete`, { token: learning.token }); setDone(true); } catch (e) { setError(e instanceof Error ? e.message : "完成校验失败"); } finally { setBusy(false); } }} /> : <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]"><Card className="course-overview"><div className="flex flex-col gap-6 p-1 sm:p-4"><div className="flex flex-wrap gap-2"><Tag color="green">{trainingCategories.find(c => c.value === course.category)?.label || "未分类"}</Tag><Tag>{course.requirementType === "ELECTIVE" ? "选修" : "必修"}</Tag><Tag>独立培训</Tag></div><h1 className="break-words text-2xl font-semibold leading-relaxed sm:text-3xl">{course.name}</h1><p className="whitespace-pre-wrap break-words leading-7 text-slate-500">{course.description || "请按照学习目录完成本次培训材料。"}</p><div className="grid grid-cols-3 gap-3 rounded-lg bg-slate-50 p-4 text-center text-sm"><span><IconPlay /><span className="mt-2 block">视频学习</span></span><span><IconFile /><span className="mt-2 block">文档阅读</span></span><span><IconImage /><span className="mt-2 block">图片学习</span></span></div><div className="border-t border-slate-100 pt-5"><h2 className="font-semibold">学习流程</h2><ol className="mt-4 flex flex-col gap-3 text-sm text-slate-500"><li>1. 填写姓名和手机号，确认学习资格</li><li>2. 查看学习目录，完成本次培训材料</li><li>3. 提交培训完成{course.surveyEnabled ? "，填写满意度评估" : ""}</li></ol></div></div></Card><Card className="course-identity"><div className="flex items-center gap-2"><h2 className="text-lg font-semibold">开始学习</h2><Tooltip content="请填写培训名单中的姓名和手机号。使用相同信息再次进入，可恢复已保存的学习进度。"><Button theme="borderless" type="tertiary" icon={<IconHelpCircleStroked />} aria-label="学习身份说明" /></Tooltip></div><Form initValues={identity} onSubmit={values => void enter({ name: String(values.name).trim(), phone: String(values.phone).trim() })}><Form.Input field="name" label="姓名" placeholder="请输入姓名" rules={[{ required: true, whitespace: true, message: "请输入姓名" }]} /><Form.Input field="phone" label="手机号" placeholder="请输入手机号" rules={[{ required: true, message: "请输入手机号" }, { pattern: /^1\d{10}$/, message: "请输入正确的手机号" }]} /><Button htmlType="submit" theme="solid" size="large" block loading={busy} className="!mt-4">开始 / 继续学习</Button></Form><p className="mt-4 text-center text-xs leading-6 text-slate-500">自动保存学习进度，退出后可继续</p></Card></div>}
    </Layout.Content></Layout>;
}
