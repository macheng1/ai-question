package com.aiquestion.ai;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConsultationHistory {
    record Summary(String conversationId, String title, Long trainingId, Long departmentId) {}
    record Turn(long id, String question, TrainingConsultation.Answer answer) {}
    record Row(long id, String question, String text, TrainingConsultation.Topic topic,
               String tab, String status, int total) {
        Turn turn() {
            return new Turn(id, question, new TrainingConsultation.Answer(text, topic, tab, status, total));
        }
    }

    int deleteConversation(@Param("owner") long owner, @Param("thread") String thread);

    List<Summary> list(long owner);
    List<Row> rows(@Param("owner") long owner, @Param("thread") String thread);
    List<Row> recentRows(@Param("owner") long owner, @Param("thread") String thread,
                         @Param("training") Long training, @Param("department") Long department);

    default List<Turn> turns(long owner, UUID conversation) {
        return rows(owner, conversation.toString()).stream().map(Row::turn).toList();
    }

    default List<Turn> recent(long owner, TrainingConsultationController.Question q) {
        return recentRows(owner, q.conversationId().toString(), q.trainingId(), q.departmentId())
                .stream().map(Row::turn).toList();
    }

    void lockThread(@Param("owner") long owner, @Param("thread") String thread);
    boolean isDeleted(@Param("owner") long owner, @Param("thread") String thread);
    void markDeleted(@Param("owner") long owner, @Param("thread") String thread);

    void insert(@Param("owner") long owner, @Param("thread") String thread,
                @Param("q") TrainingConsultationController.Question question,
                @Param("a") TrainingConsultation.Answer answer);
}
