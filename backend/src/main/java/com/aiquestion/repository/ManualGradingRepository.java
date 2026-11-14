package com.aiquestion.repository;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ManualGradingRepository {
    record Pending(long attemptId, String name, int attemptNo, java.time.LocalDateTime submittedAt) {}
    record Question(long questionId, String stem, String referenceAnswer, String answer, BigDecimal maxScore) {}

    List<Pending> pending(@Param("taskId") long taskId, @Param("owner") long owner);

    List<Question> questions(@Param("taskId") long taskId, @Param("attemptId") long attemptId, @Param("owner") long owner);

    String lockStatus(@Param("taskId") long taskId, @Param("attemptId") long attemptId, @Param("owner") long owner);

    void grade(@Param("attemptId") long attemptId, @Param("question") Question question,
               @Param("score") BigDecimal score, @Param("correct") boolean correct);

    int finish(@Param("attemptId") long attemptId, @Param("owner") long owner,
               @Param("score") BigDecimal score, @Param("passed") boolean passed);
}
