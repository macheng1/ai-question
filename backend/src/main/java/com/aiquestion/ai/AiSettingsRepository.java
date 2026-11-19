package com.aiquestion.ai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiSettingsRepository {
    record Configuration(String provider, String baseUrl, String model) {}
    record Connection(String baseUrl, String model, byte[] encryptedApiKey) {}
    Connection connection(long owner);
    Configuration configuration(long owner);
    int updateModel(@Param("owner") long owner, @Param("provider") String provider, @Param("baseUrl") String baseUrl, @Param("model") String model);
    void save(@Param("owner") long owner, @Param("provider") String provider, @Param("baseUrl") String baseUrl, @Param("model") String model, @Param("secret") byte[] secret);
}
