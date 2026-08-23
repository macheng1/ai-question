package com.aiquestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="aliyun.oss")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public record AliyunOssProperties(String accessKeyId,String accessKeySecret,String endpoint,String bucket,String region) {}
