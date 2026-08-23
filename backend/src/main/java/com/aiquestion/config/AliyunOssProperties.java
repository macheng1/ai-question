package com.aiquestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="aliyun.oss")
public record AliyunOssProperties(String accessKeyId,String accessKeySecret,String endpoint,String bucket,String region) {}
