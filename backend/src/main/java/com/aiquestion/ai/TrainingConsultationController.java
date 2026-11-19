package com.aiquestion.ai;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/ai/training-consultation")
public class TrainingConsultationController {
    public record Question(@Positive Long trainingId, @Positive Long departmentId,
            @NotBlank @Size(max = 1000) String question, @NotNull UUID conversationId) {}

    private final TrainingConsultation consultation;

    private final ConsultationHistory history;

    private final ObjectMapper json;
    private final ConsultationWriter writer;

    public TrainingConsultationController(TrainingConsultation consultation, ConsultationHistory history, ObjectMapper json, ConsultationWriter writer) {
        this.writer = writer;
        this.json = json;
        this.consultation = consultation;
        this.history = history;
    }

    @GetMapping("/history")
    public ApiResponse<List<ConsultationHistory.Summary>> history() {
        return ApiResponse.ok(history.list(ManagementAccess.userId()));
    }

    @DeleteMapping("/history/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable UUID id) {
        writer.delete(ManagementAccess.userId(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/history/{id}")
    public ApiResponse<List<ConsultationHistory.Turn>> turns(@PathVariable UUID id) {
        return ApiResponse.ok(history.turns(ManagementAccess.userId(), id));
    }

    @PostMapping(value = "/stream", produces = "application/x-ndjson")
    public void stream(@Valid @RequestBody Question question, HttpServletResponse response) throws IOException {
        long owner = ManagementAccess.userId();
        var recent = history.recent(owner, question);
        var facts = consultation.ask(owner, question, recent);
        var chunks = consultation.explain(owner, question.question(), facts, recent);
        response.setContentType("application/x-ndjson;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        var out = response.getOutputStream();
        StringBuilder text = new StringBuilder();
        try (var stream = chunks.toStream(1)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                String chunk = iterator.next();
                if (chunk.isEmpty()) continue;
                text.append(chunk);
                send(out, "delta", chunk);
            }
        } catch (RuntimeException e) {
            send(out, "error", "模型回复中断，请重试或检查 AI 配置");
            return;
        }
        if (text.toString().isBlank()) {
            send(out, "error", "模型未返回内容，请重试");
            return;
        }
        var answer = new TrainingConsultation.Answer(text.toString(), facts.topic(), facts.tab(), facts.status(), facts.total());
        try {
            writer.save(owner, question, answer);
        } catch (com.aiquestion.common.BusinessException e) {
            send(out, "error", e.getMessage());
            return;
        } catch (org.springframework.dao.DataAccessException e) {
            send(out, "error", "回复已生成，但未能保存到历史记录");
            return;
        }
        send(out, "done", answer);
    }

    private void send(OutputStream out, String type, Object data) throws IOException {
        out.write(json.writeValueAsBytes(Map.of("type", type, "data", data)));
        out.write('\n');
        out.flush();
    }

    @PostMapping
    public ApiResponse<TrainingConsultation.Answer> ask(@Valid @RequestBody Question question) {
        long owner = ManagementAccess.userId();
        var recent = history.recent(owner, question);
        var answer = consultation.ask(owner, question, recent);
        if (answer.topic() == TrainingConsultation.Topic.RECAP) {
            String text = consultation.explain(owner, question.question(), answer, recent)
                    .collect(java.util.stream.Collectors.joining()).block();
            answer = new TrainingConsultation.Answer(text, answer.topic(), null, null, 0);
        }
        writer.save(owner, question, answer);
        return ApiResponse.ok(answer);
    }
}
