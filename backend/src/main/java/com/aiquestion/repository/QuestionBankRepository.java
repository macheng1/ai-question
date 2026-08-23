package com.aiquestion.repository;

import com.aiquestion.common.PageQuery;
import com.aiquestion.domain.QuestionBank;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis Mapper 只负责 SQL 映射，不承载业务逻辑。 */
@Mapper
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public interface QuestionBankRepository {
  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  List<QuestionBank> findPage(PageQuery query);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  long count(PageQuery query);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  List<QuestionBank> findOwnedPage(@Param("query") PageQuery query, @Param("userId") Long userId);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  long countOwned(@Param("query") PageQuery query, @Param("userId") Long userId);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  List<QuestionBank> findPublished(PageQuery query);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  long countPublished(PageQuery query);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  QuestionBank findPublishedById(Long id);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  QuestionBank findById(Long id);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  QuestionBank findOwnedById(@Param("id") Long id, @Param("userId") Long userId);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  int insert(QuestionBank bank);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  int updateOwned(@Param("bank") QuestionBank bank, @Param("userId") Long userId);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  int incrementQuestionCount(Long id);

  /**
   * 数据访问方法。
   *
   * @param query 查询条件
   * @param startAt 开始时间
   * @param endAt 结束时间
   * @param userId 用户标识
   * @param bankId 题库标识
   * @param stem 题干
   * @param score 分值
   * @param sortNo 排序号
   * @param id 数据标识
   * @param bank 题库
   * @param category 分类
   * @param item 题目
   * @param task 考试任务
   * @param paper 试卷
   * @param status 状态
   * @param option 选项
   * @param paperId 试卷标识
   * @param questionId 题目标识
   * @param includeRetake 是否包含补考
   * @return 数据访问结果
   */
  int decrementQuestionCount(Long id);
}
