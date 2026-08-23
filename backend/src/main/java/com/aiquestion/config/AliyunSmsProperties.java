package com.aiquestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="aliyun.sms")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public record AliyunSmsProperties(String accessKeyId,String accessKeySecret,String endpoint,String signName,String templateCode) {}
