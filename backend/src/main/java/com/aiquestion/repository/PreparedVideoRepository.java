package com.aiquestion.repository;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PreparedVideoRepository {
    record Ticket(String id, long owner, String name, long size, String key, String phase, long expires, int partSize) {
        @org.apache.ibatis.annotations.AutomapConstructor
        public Ticket {}

        public Ticket(String id, long owner, String name, long size, String key, String phase, long expires) {
            this(id, owner, name, size, key, phase, expires, 8 * 1024 * 1024);
        }
    }
    void attach(@Param("id") String id, @Param("upload") String upload);
    String multipartId(@Param("id") String id);
    Long lockOwner(@Param("owner") long owner);
    int pendingCount(@Param("owner") long owner);
    void reserve(Ticket ticket);
    Ticket find(@Param("id") String id);
    Ticket lock(@Param("id") String id);
    void advance(@Param("id") String id, @Param("phase") String phase, @Param("expires") long expires);
    void remove(@Param("id") String id);
    List<String> expired(@Param("now") long now);
}
