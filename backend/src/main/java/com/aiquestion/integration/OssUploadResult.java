package com.aiquestion.integration;

/**
 * 后端业务组件。
 *
 * @author aiquestion
 */

public record OssUploadResult(String objectKey, String originalName, String url, long size, String contentType) {
}
