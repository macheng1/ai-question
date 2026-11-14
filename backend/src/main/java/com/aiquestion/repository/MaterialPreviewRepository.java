package com.aiquestion.repository;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MaterialPreviewRepository {
  List<Long> pending();
  // Must acquire and release on the SAME physical connection. MySQL releases on disconnect.
  Integer acquire(@Param("id") long id);
  Integer release(@Param("id") long id);
  int claim(@Param("id") long id);
  Map<String,Object> material(@Param("id") long id);
  List<Map<String,Object>> pages(@Param("id") long id);
  int reserve(@Param("id") long id,@Param("page") int page,@Param("key") String key);
  void pageReady(@Param("id") long id,@Param("page") int page);
  int ready(@Param("id") long id);
  void failed(@Param("id") long id);
  int retry(@Param("id") long id);
}
