package com.aiquestion.repository;

import com.aiquestion.domain.QuestionBankCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public interface QuestionBankCategoryRepository {
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
  List<QuestionBankCategory> findAll();

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
  List<QuestionBankCategory> findOwned(@Param("userId") Long userId);

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
  List<QuestionBankCategory> findPublicRoots();

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
  QuestionBankCategory findById(Long id);

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
  QuestionBankCategory findOwnedById(@Param("id") Long id, @Param("userId") Long userId);

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
  int insert(QuestionBankCategory category);

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
  int update(QuestionBankCategory category);

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
  int deleteById(QuestionBankCategory category);
}
