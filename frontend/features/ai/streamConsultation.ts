import { apiClient } from "@/lib/api/client";

export type ConsultationAnswer = { text: string; topic: string; tab: string | null; status: string | null; total: number };
type Event = { type: "delta" | "error"; data: string } | { type: "done"; data: ConsultationAnswer };

export async function streamConsultation(
  question: { question: string; conversationId: string; trainingId?: number; departmentId?: number },
  signal: AbortSignal, onText: (text: string) => void
): Promise<ConsultationAnswer> {
  const body = await apiClient.post<ReadableStream<Uint8Array>>("/management/ai/training-consultation/stream",
    question, { adapter: "fetch", responseType: "stream", signal, timeout: 150000 });
  const reader = body.pipeThrough(new TextDecoderStream()).getReader();
  let pending = "";
  let text = "";
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) throw new Error("回复连接已中断，请重试");
      pending += chunk.value;
      let end: number;
      while ((end = pending.indexOf("\n")) >= 0) {
        const line = pending.slice(0, end);
        pending = pending.slice(end + 1);
        if (!line.trim()) continue;
        const event: Event = JSON.parse(line);
        if (event.type === "done") return event.data;
        if (event.type === "error") throw new Error(event.data);
        text += event.data;
        onText(text);
      }
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
