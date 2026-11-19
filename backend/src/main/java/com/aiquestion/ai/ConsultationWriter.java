package com.aiquestion.ai;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultationWriter {
    private final ConsultationHistory history;

    ConsultationWriter(ConsultationHistory history) {
        this.history = history;
    }

    // Both operations lock the same state row, including for conversations created before V65.
    @Transactional
    public void save(long owner, TrainingConsultationController.Question question, TrainingConsultation.Answer answer) {
        String thread = question.conversationId().toString();
        history.lockThread(owner, thread);
        if (history.isDeleted(owner, thread)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该对话已删除，请开启新对话");
        }
        history.insert(owner, thread, question, answer);
    }

    @Transactional
    public void delete(long owner, UUID id) {
        String thread = id.toString();
        history.markDeleted(owner, thread);
        history.deleteConversation(owner, thread);
    }
}
