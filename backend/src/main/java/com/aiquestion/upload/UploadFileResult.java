package com.aiquestion.upload;
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */

public record UploadFileResult(String objectKey,String originalName,String url,long size,String contentType) {}
