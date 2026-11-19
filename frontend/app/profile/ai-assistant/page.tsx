"use client";

import { useEffect, useRef, useState } from "react";
import { AIChatDialogue, AIChatInput, Button, Modal, Select, Toast } from "@douyinfe/semi-ui";
import { IconDeleteStroked, IconArrowUpRight, IconBookOpenStroked, IconClockStroked, IconHistogram } from "@douyinfe/semi-icons";
import styles from "./assistant.module.css";
import type { Message } from "@douyinfe/semi-ui/lib/es/aiChatDialogue/interface";
import { useAuthStore } from "@/features/auth/store";

import { ProfileShell } from "@/components/layout/ProfileShell";
import { getDashboardOptions } from "@/features/dashboard/api";
import type { DashboardOptions } from "@/features/dashboard/types";
import { ConsultationRoster, type RosterQuery } from "@/features/ai/ConsultationRoster";
import { apiClient } from "@/lib/api/client";

import { showConsultationError } from "@/features/ai/showConsultationError";
import { streamConsultation, type ConsultationAnswer as Answer } from "@/features/ai/streamConsultation";
type Conversation = { conversationId: string; title: string; trainingId: number | null; departmentId: number | null };
type Turn = { id: number; question: string; answer: Answer };
const endpoint = "/management/ai/training-consultation";

function newConversationId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, n => n.toString(16).padStart(2, "0")).join("");
  return [hex.slice(0,8), hex.slice(8,12), hex.slice(12,16), hex.slice(16,20), hex.slice(20)].join("-");
}

function reply(id: string, answer: Answer, training: number, department: number): Message {
  const roster = answer.tab && answer.status ? { trainingId: training || undefined,
    departmentId: department || undefined, tab: answer.tab, status: answer.status } : undefined;
  return { id: id + "-answer", role: "assistant", content: answer.text, roster, total: answer.total };
}

const examples = [
  { title: "培训完成情况", question: "培训完成情况怎么样？", icon: IconHistogram },
  { title: "逾期学员", question: "哪些学员逾期未完成？", icon: IconClockStroked },
  { title: "考试通过情况", question: "哪些人已经学完但考试没通过？", icon: IconBookOpenStroked },
];
const roles = { user: { name: "我" }, assistant: { name: "培训数据助手" } };

export default function TrainingAssistantPage() {
  const [options, setOptions] = useState<DashboardOptions>();
  const [loadFailed, setLoadFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const [training, setTraining] = useState(0);
  const [department, setDepartment] = useState(0);
  const [messages, setMessages] = useState<Message[]>([]);
  const user = useAuthStore(s => s.user);
  const [conversationId, setConversationId] = useState("");
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historySearch, setHistorySearch] = useState("");
  const [sessions, setSessions] = useState<Conversation[]>([]);
  const [restoring, setRestoring] = useState(true);
  const loadId = useRef(0);
  const storageKey = user ? `training-chat:${user.enterpriseId}:${user.userId}` : "";
  async function openHistory(session: Conversation, key: string) {
    const version = ++loadId.current;
    setRestoring(true);
    try {
      const turns = await apiClient.get<Turn[]>(endpoint + "/history/" + session.conversationId);
      if (version !== loadId.current) return;
      const t = session.trainingId ?? 0, d = session.departmentId ?? 0;
      setHistoryOpen(false);
      setTraining(t);
      setDepartment(d);
      setConversationId(session.conversationId);
      setMessages(turns.flatMap(turn => [
        { id: String(turn.id), role: "user", content: turn.question },
        reply(String(turn.id), turn.answer, t, d)
      ]));
      setRoster(undefined);
      sessionStorage.setItem(key, session.conversationId);
    } catch (e) {
      if (version === loadId.current) showConsultationError(e, "读取对话失败");
    } finally {
      if (version === loadId.current) setRestoring(false);
    }
  }
  useEffect(() => {
    if (!storageKey) return;
    const version = ++loadId.current;
    setRestoring(true);
    apiClient.get<Conversation[]>(endpoint + "/history").then(async list => {
      if (version !== loadId.current) return;
      setSessions(list);
      const current = list.find(item => item.conversationId === sessionStorage.getItem(storageKey));
      if (current) await openHistory(current, storageKey);
    }).catch(e => { if (version === loadId.current) { setLoadFailed(true); showConsultationError(e, "读取历史失败"); } })
      .finally(() => { if (version === loadId.current) setRestoring(false); });
    return () => { loadId.current++; };
  }, [storageKey, retry]);
  const [roster, setRoster] = useState<RosterQuery>();
  const [busy, setBusy] = useState(false);
  const pending = useRef<AbortController | null>(null);
  const bottom = useRef<HTMLDivElement>(null);
  useEffect(() => {
    let active = true;
    getDashboardOptions().then(result => { if (active) setOptions(result); })
      .catch(e => { if (active) { setLoadFailed(true); showConsultationError(e, "读取培训失败"); } });
    return () => { active = false; };
  }, [retry]);
  useEffect(() => () => pending.current?.abort(), []);
  useEffect(() => { bottom.current?.scrollIntoView({ block: "nearest" }); }, [messages]);
  function clearConversation() {
    setHistoryOpen(false);
    setMessages([]);
    setConversationId("");
    sessionStorage.removeItem(storageKey);
    setRoster(undefined);
  }
  async function ask(raw: string) {
    const question = raw.trim();
    if (!question || pending.current || restoring || !options) return;
    if (question.length > 1000) { Toast.error("问题最多可输入 1000 字"); return; }
    const abort = new AbortController();
    pending.current = abort;
    setBusy(true);
    const filters = { trainingId: training || undefined, departmentId: department || undefined };
    const thread = conversationId || newConversationId();
    setConversationId(thread);
    const id = String(Date.now());
    setMessages(list => [...list, { id, role: "user", content: question }]);
    try {
      const answerId = id + "-answer";
      const updateReply = (message: Message) => setMessages(list =>
        list.at(-1)?.id === answerId ? [...list.slice(0, -1), message] : [...list, message]);
      const answer = await streamConsultation({ ...filters, conversationId: thread, question }, abort.signal,
        text => { if (!abort.signal.aborted) updateReply({ id: answerId, role: "assistant", content: text }); });
      if (abort.signal.aborted) return;
      updateReply(reply(id, answer, training, department));
      sessionStorage.setItem(storageKey, thread);
      setSessions(list => [
        list.find(s => s.conversationId === thread) ?? {
          conversationId: thread, title: question, trainingId: training || null, departmentId: department || null
        }, ...list.filter(s => s.conversationId !== thread)
      ]);
    } catch (e) {
      if (!abort.signal.aborted) showConsultationError(e, "查询失败，请重试");
    } finally {
      pending.current = null;
      setBusy(false);
    }
  }
  function confirmDelete(session: Conversation) {
    Modal.confirm({
      title: "删除这段对话？",
      content: `“${session.title}”中的全部消息将被删除。`,
      okText: "删除",
      cancelText: "取消",
      okButtonProps: { type: "danger" },
      onOk: async () => {
        try {
          await apiClient.delete(endpoint + "/history/" + session.conversationId);
          setSessions(list => list.filter(item => item.conversationId !== session.conversationId));
          if (session.conversationId === conversationId) clearConversation();
          Toast.success("对话已删除");
        } catch (e) {
          showConsultationError(e, "删除失败，请重试");
        }
      },
    });
  }
  const visibleSessions = sessions.filter(session => session.title.includes(historySearch.trim()));
  return <ProfileShell><section className={styles.workspace}>
    <aside className={styles.sessionRail} aria-label="对话导航">
      <div className={styles.railActions}>
        <button type="button" className={styles.newChat} disabled={busy || restoring} onClick={clearConversation}>
          <span aria-hidden="true">⊕</span> 开启新对话
        </button>
        <button type="button" className={styles.historyToggle} aria-controls="assistant-sessions"
          aria-expanded={historyOpen} onClick={() => setHistoryOpen(!historyOpen)}>对话历史</button>
      </div>
      <nav id="assistant-sessions" className={styles.sessionList} data-open={historyOpen} aria-label="最近对话">
        <h2>最近对话</h2>
        {(sessions.length > 1 || historySearch) && <input type="search" aria-label="搜索对话" placeholder="搜索对话"
          value={historySearch} onChange={e => setHistorySearch(e.target.value)}/>}
        {visibleSessions.map(session => <div className={styles.sessionRow} key={session.conversationId}>
          <button type="button"
          aria-current={conversationId === session.conversationId ? "page" : undefined}
          title={session.title} disabled={busy || restoring} onClick={() => void openHistory(session, storageKey)}>
          {session.title}
          </button>
          <button type="button" className={styles.deleteChat} title="删除对话" aria-label={`删除对话：${session.title}`}
            disabled={busy || restoring} onClick={() => confirmDelete(session)}><IconDeleteStroked/></button>
        </div>)}
        {!visibleSessions.length && <p>{restoring ? "正在读取…" : historySearch ? "没有匹配的对话" : "暂无历史对话"}</p>}
      </nav>
    </aside>
    <div className={styles.panel}>
    <header className={styles.toolbar}>
    <h1>培训数据咨询</h1>
    <div className={styles.filters}>
      {([{label:"培训", value:training, list:options?.trainings, change:setTraining},
        {label:"部门", value:department, list:options?.departments, change:setDepartment}]).map(filter =>
        <Select key={filter.label} aria-label={filter.label} value={filter.value} disabled={busy || restoring || !options}
          className={styles.filter} filter optionList={[{ value: 0, label: "全部" + filter.label },
            ...(filter.list ?? []).map(item => ({ value:item.id, label:item.name }))]}
          onChange={value => { filter.change(Number(value)); clearConversation(); }}/>)}
    </div>
    </header>
    {loadFailed && <Button disabled={busy || restoring} onClick={() => {
      setLoadFailed(false);
      setRetry(value => value + 1);
    }}>重新加载</Button>}
    <div className={styles.content}>
      <main className={messages.length ? styles.chat : styles.welcome}>
        {!restoring && messages.length === 0 && <div className={styles.hero}>
          <span className={styles.mark} aria-hidden="true"><i/><i/><i/><i/></span>
          <h2>你好，我是培训数据助手</h2>
          <p>我可以帮你看培训进度、找出逾期未完成的学员，或查看考试通过情况。今天想先了解哪一项？</p>
        </div>}
        {messages.length > 0 && <div className={styles.history}>
          <AIChatDialogue chats={messages} roleConfig={roles} mode="userBubble" escapeHtml
            dialogueRenderConfig={{ renderDialogueAvatar: ({message}) => message?.role === "user"
              ? <span className={styles.userAvatar} aria-label="我的头像">{Array.from(user?.name?.trim() || "我")[0]}</span>
              : <span className={styles.botAvatar} aria-label="培训数据助手头像"><IconHistogram/></span>,
              renderDialogueAction: ({message}) => message?.roster && message.total > 0
              ? <Button size="small" onClick={() => setRoster(message.roster)}>查看名单（{message.total}）</Button> : null }}/>
          {busy && messages.at(-1)?.role === "user" && (
            <div className={styles.typing} role="status" aria-label="助手正在回复">
              <span/><span/><span/>
            </div>
          )}
          <div ref={bottom}/>
        </div>}
        <div className={styles.composer}><AIChatInput placeholder={messages.length ? "继续追问…" : "例如：哪些学员还没完成培训？"} immediatelyRender={false} generating={busy}
          showUploadButton={false} showTemplateButton={false}
          onStopGenerate={() => pending.current?.abort()}
          onMessageSend={value => void ask((value.inputContents ?? []).map(part => part.text ?? "").join(""))}/>
        </div>
        {messages.length === 0 && <div className={styles.prompts}>
          {examples.map(({ title, question, icon: Icon }) =>
            <button key={title} type="button" disabled={!options || busy || restoring} onClick={() => void ask(question)}>
              <span className={styles.promptHeading}><Icon/><strong>{title}</strong><IconArrowUpRight/></span>
              <span>{question}</span>
            </button>)}
        </div>}
      </main>
      {roster && <aside className="w-full xl:w-80"><ConsultationRoster key={JSON.stringify(roster)} query={roster} close={() => setRoster(undefined)}/></aside>}
    </div>
    </div>
  </section></ProfileShell>;
}
