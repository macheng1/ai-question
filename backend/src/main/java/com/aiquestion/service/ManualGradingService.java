package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.repository.ManualGradingRepository;
import com.aiquestion.repository.PublicExamRepository;
import com.aiquestion.repository.RepositoryRows;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualGradingService {
    private final ManualGradingRepository grading;
    private final PublicExamRepository exams;

    public ManualGradingService(ManualGradingRepository grading, PublicExamRepository exams) {
        this.grading = grading;
        this.exams = exams;
    }

    public List<ManualGradingRepository.Pending> pending(long task, long owner) {
        return grading.pending(task, owner);
    }

    public List<ManualGradingRepository.Question> questions(long task, long attempt, long owner) {
        var rows = grading.questions(task, attempt, owner);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "待阅答卷不存在或已完成阅卷");
        return rows;
    }

    @Transactional
    public void finish(long task, long attempt, long owner, Map<Long, BigDecimal> scores) {
        String status = grading.lockStatus(task, attempt, owner);
        if (status == null) throw new BusinessException(ErrorCode.NOT_FOUND, "答卷不存在");
        if (!"PENDING_REVIEW".equals(status)) throw new BusinessException(ErrorCode.BAD_REQUEST, "答卷不处于待阅卷状态");
        var questions = questions(task, attempt, owner);
        if (scores == null || scores.size() != questions.size())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请为每一道问答题评分");
        for (var question : questions) {
            BigDecimal score = scores.get(question.questionId());
            if (score == null || score.signum() < 0 || score.compareTo(question.maxScore()) > 0
                    || score.stripTrailingZeros().scale() > 2)
                throw new BusinessException(ErrorCode.BAD_REQUEST, "每题得分须在零分和本题满分之间，最多两位小数");
            grading.grade(attempt, question, score, score.compareTo(question.maxScore()) == 0);
        }
        BigDecimal total = RepositoryRows.one(exams.sumAnswerScores(attempt));
        var row = RepositoryRows.one(exams.findAttemptForSubmission(attempt));
        BigDecimal passScore = new BigDecimal(row.get("pass_score").toString());
        grading.finish(attempt, owner, total, total.compareTo(passScore) >= 0);
    }
}
