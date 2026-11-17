package com.aiquestion.repository;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PreparedMaterialRepository {
    record Ticket(String id, long owner, String title, String type, String key, int pages, long expires) {}

    void insert(Ticket ticket);

    Ticket lock(@Param("id") String id);

    void remove(@Param("id") String id);

    List<String> expired(@Param("now") long now);
}
