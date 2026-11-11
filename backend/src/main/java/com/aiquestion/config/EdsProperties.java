package com.aiquestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** EDS S3 连接参数，凭证仅从部署环境读取。 */
@ConfigurationProperties(prefix = "storage.eds")
public record EdsProperties(String endpoint, String bucket, String accessKeyId,
    String secretKey, String region) {}
