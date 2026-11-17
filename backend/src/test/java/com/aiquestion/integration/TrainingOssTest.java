package com.aiquestion.integration;

import com.aiquestion.config.AliyunOssProperties;
import com.aiquestion.storage.FileStorageService;
import com.aiquestion.service.TrainingVideoStorage;
import com.aiquestion.service.TrainingVideoCache;
import com.aiquestion.common.BusinessException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.io.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingOssTest {
 @TempDir Path directory;
 private final OSS client=mock(OSS.class);
 private AliyunOssService service() {
  return new AliyunOssService(new AliyunOssProperties("key","secret","endpoint","bucket","region")) {
   @Override protected OSS createClient() { return client; }
  };
 }
 @Test void uploadUsesPrivateAclShortKeyAndServerContentType() {
  var key="training-videos/12345678-1234-1234-1234-123456789abc.mp4";
  service().put(new MockMultipartFile("file","a".repeat(250)+".mp4","text/plain",new byte[12]),"video/mp4",key);
  var capture=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
  verify(client).putObject(capture.capture()); verify(client,never()).shutdown();
  assertTrue(key.matches("training-videos/[a-f0-9-]{36}\\.mp4"));
  assertEquals("private",capture.getValue().getMetadata().getRawMetadata().get("x-oss-object-acl"));
  assertEquals("video/mp4",capture.getValue().getMetadata().getContentType());
  assertTrue(("oss:"+key).length()<100);
 }
 @Test void lostUploadResponseRequiresMatchingStoredContent() {
  var key = "training-videos/12345678-1234-1234-1234-123456789abc.mp4";
  var file = new MockMultipartFile("file", "test.mp4", "video/mp4", new byte[]{1, 2, 3});
  when(client.putObject(any(PutObjectRequest.class))).thenThrow(new com.aliyun.oss.ClientException("Read timed out"));
  var stored = mock(ObjectMetadata.class);
  when(client.getObjectMetadata("bucket", key)).thenReturn(stored);
  when(stored.getContentLength()).thenReturn(3L);
  when(stored.getETag()).thenReturn("5289DF737DF57326FCDD22597AFB1FAC");
  assertDoesNotThrow(() -> service().put(file, "video/mp4", key));
  when(stored.getETag()).thenReturn("different");
  assertThrows(BusinessException.class, () -> service().put(file, "video/mp4", key));
 }
 @Test void multipartCompletionAllowsSdkToSortPartTags() {
  when(client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenAnswer(call -> {
   var tags = call.<CompleteMultipartUploadRequest>getArgument(0).getPartETags();
   tags.sort(java.util.Comparator.comparingInt(PartETag::getPartNumber));
   assertEquals(1, tags.get(0).getPartNumber());
   return null;
  });
  service().finishVideo("key", "upload", java.util.List.of(
      new com.aiquestion.storage.VideoMultipartStorage.Part(2, 12, "b"),
      new com.aiquestion.storage.VideoMultipartStorage.Part(1, 8388608, "a")));
 }

 @Test void signedUrlUsesOnlyTheRequestedKeyUploadAndPart() throws Exception {
  when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class))).thenReturn(
      java.net.URI.create("https://example.invalid/test").toURL());
  service().signVideoPart("training-videos/key.mp4", "upload", 17);
  var capture = org.mockito.ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
  verify(client).generatePresignedUrl(capture.capture());
  var request = capture.getValue();
  assertEquals(com.aliyun.oss.HttpMethod.PUT, request.getMethod());
  assertEquals("training-videos/key.mp4", request.getKey());
  assertEquals("application/octet-stream", request.getContentType());
  assertEquals(Map.of("uploadId", "upload", "partNumber", "17"), request.getQueryParameter());
 }

 @Test void closingStreamDoesNotCloseSharedClient() throws Exception {
  var obj=new OSSObject(); var metadata=new ObjectMetadata(); metadata.setContentLength(3);
  obj.setObjectMetadata(metadata);obj.setObjectContent(new ByteArrayInputStream(new byte[]{1,2,3}));
  when(client.getObject("bucket","key")).thenReturn(obj);
  var opened=service().open("key");verify(client,never()).shutdown();
  assertEquals(3,opened.length());assertArrayEquals(new byte[]{1,2,3},opened.stream().readAllBytes());
  opened.stream().close();verify(client,never()).shutdown();
 }
 @Test void readFailureDoesNotCloseSharedClient() {
  when(client.getObject("bucket","missing")).thenThrow(new RuntimeException());
  assertThrows(BusinessException.class,()->service().open("missing"));verify(client,never()).shutdown();
 }
 @Test void legacyVideosRemainReadableAndCannotEscapeDirectory() throws Exception {
  var oss=mock(FileStorageService.class);var storage=new TrainingVideoStorage(oss,directory.toString(),new TrainingVideoCache(directory.toString(),0,0));
  Files.write(directory.resolve("old.mp4"),new byte[]{1,2,3});
  var response=storage.content(Map.of("storage_path","old.mp4","content_type","video/mp4"));
  assertArrayEquals(new byte[]{1,2,3},response.getBody().getInputStream().readAllBytes());
  assertThrows(BusinessException.class,()->storage.delete("../outside.mp4"));verifyNoInteractions(oss);
 }
 @Test void deletionUsesOssOnlyForValidTrainingObjects() {
  var oss=mock(FileStorageService.class);var storage=new TrainingVideoStorage(oss,directory.toString(),new TrainingVideoCache(directory.toString(),0,0));
  var key="training-videos/12345678-1234-1234-1234-123456789abc.mp4";
  storage.delete("oss:"+key);verify(oss).delete("oss:"+key);
  assertThrows(BusinessException.class,()->storage.delete("oss:other/file"));
 }
}
