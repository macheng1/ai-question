package com.aiquestion.integration;

import com.aiquestion.config.AliyunOssProperties;
import com.aiquestion.service.TrainingVideoStorage;
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
  var key=service().uploadTrainingVideo(new MockMultipartFile("file","a".repeat(250)+".mp4","text/plain",new byte[12]),"video/mp4");
  var capture=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
  verify(client).putObject(capture.capture()); verify(client).shutdown();
  assertTrue(key.matches("training-videos/[a-f0-9-]{36}\\.mp4"));
  assertEquals("private",capture.getValue().getMetadata().getRawMetadata().get("x-oss-object-acl"));
  assertEquals("video/mp4",capture.getValue().getMetadata().getContentType());
  assertTrue(("oss:"+key).length()<100);
 }
 @Test void streamClosesOssClientOnlyWhenConsumerCloses() throws Exception {
  var obj=new OSSObject(); var metadata=new ObjectMetadata(); metadata.setContentLength(3);
  obj.setObjectMetadata(metadata);obj.setObjectContent(new ByteArrayInputStream(new byte[]{1,2,3}));
  when(client.getObject("bucket","key")).thenReturn(obj);
  var opened=service().openTrainingVideo("key");verify(client,never()).shutdown();
  assertEquals(3,opened.length());assertArrayEquals(new byte[]{1,2,3},opened.stream().readAllBytes());
  opened.stream().close();verify(client).shutdown();
 }
 @Test void failuresReleaseOssClient() {
  when(client.getObject("bucket","missing")).thenThrow(new RuntimeException());
  assertThrows(BusinessException.class,()->service().openTrainingVideo("missing"));verify(client).shutdown();
 }
 @Test void legacyVideosRemainReadableAndCannotEscapeDirectory() throws Exception {
  var oss=mock(AliyunOssService.class);var storage=new TrainingVideoStorage(oss,directory.toString());
  Files.write(directory.resolve("old.mp4"),new byte[]{1,2,3});
  var response=storage.content(Map.of("storage_path","old.mp4","content_type","video/mp4"));
  assertArrayEquals(new byte[]{1,2,3},response.getBody().getInputStream().readAllBytes());
  assertThrows(BusinessException.class,()->storage.delete("../outside.mp4"));verifyNoInteractions(oss);
 }
 @Test void deletionUsesOssOnlyForValidTrainingObjects() {
  var oss=mock(AliyunOssService.class);var storage=new TrainingVideoStorage(oss,directory.toString());
  var key="training-videos/12345678-1234-1234-1234-123456789abc.mp4";
  storage.delete("oss:"+key);verify(oss).deleteTrainingVideo(key);
  assertThrows(BusinessException.class,()->storage.delete("oss:other/file"));
 }
}
