package com.aiquestion.repository;

import com.aiquestion.domain.QuestionOption;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface QuestionOptionRepository { List<QuestionOption> findByQuestionId(Long questionId); int insert(QuestionOption option); int deleteByQuestionId(Long questionId); }
