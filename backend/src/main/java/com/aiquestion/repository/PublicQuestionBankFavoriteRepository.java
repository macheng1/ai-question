package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for PublicQuestionBankFavoriteService. SQL is defined in the matching mapper XML. */
@Mapper
public interface PublicQuestionBankFavoriteRepository {
  List<Long> findFavoriteBankIds(@Param("userId") Long userId);
  List<Integer> countPublishedBank(@Param("bankId") Long bankId);
  int insertFavorite(@Param("userId") Long userId, @Param("bankId") Long bankId);
  int deleteFavorite(@Param("userId") Long userId, @Param("bankId") Long bankId);
}
