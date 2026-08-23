package com.aiquestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/** 上传默认规则，可通过 application.yml 统一调整。接口参数可以在默认值基础上进一步收紧。 */
@ConfigurationProperties(prefix="app.upload")
public record UploadProperties(int defaultMaxSizeMb,int absoluteMaxSizeMb,int defaultMaxCount,int absoluteMaxCount,List<String> defaultAllowedExtensions) {}
