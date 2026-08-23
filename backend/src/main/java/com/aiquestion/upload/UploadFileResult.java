package com.aiquestion.upload;

public record UploadFileResult(String objectKey,String originalName,String url,long size,String contentType) {}
