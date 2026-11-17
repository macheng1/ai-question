package com.aiquestion.repository;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PreparedMaterialRepository {
    record Ticket(String id, long owner, String title, String type, String key, int pages, long expires) {}

    @Insert("INSERT INTO training_material_upload(id,owner_id,title,material_type,object_key,page_count,expires_at) VALUES(#{id},#{owner},#{title},#{type},#{key},#{pages},#{expires})")
    void insert(Ticket ticket);

    @Select("SELECT id,owner_id AS owner,title,material_type AS type,object_key AS `key`,page_count AS pages,expires_at AS expires FROM training_material_upload WHERE id=#{id} FOR UPDATE")
    Ticket lock(String id);

    @Delete("DELETE FROM training_material_upload WHERE id=#{id}")
    void remove(String id);

    @Select("SELECT id FROM training_material_upload WHERE expires_at <= #{now} ORDER BY expires_at LIMIT 100")
    List<String> expired(long now);
}
