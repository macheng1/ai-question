package com.aiquestion.integration;

public record OssUploadResult(String objectKey,String originalName,String url,long size,String contentType) {}
