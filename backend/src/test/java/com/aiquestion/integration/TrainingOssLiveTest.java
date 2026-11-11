package com.aiquestion.integration;

import com.aiquestion.config.AliyunOssProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** 仅显式启用时使用已配置的 OSS，验证后删除本次新建测试对象。 */
@EnabledIfEnvironmentVariable(named="RUN_OSS_LIVE_TEST", matches="true")
class TrainingOssLiveTest {
 @Test void uploadReadAndDeletePrivateSample() throws Exception {
  var env=System.getenv();
  var service=new AliyunOssService(new AliyunOssProperties(env.get("ALIYUN_OSS_ACCESS_KEY_ID"),env.get("ALIYUN_OSS_ACCESS_KEY_SECRET"),env.get("ALIYUN_OSS_ENDPOINT"),env.get("ALIYUN_OSS_BUCKET"),env.get("ALIYUN_OSS_REGION")));
  byte[] bytes=Files.readAllBytes(Path.of(env.get("OSS_TEST_VIDEO")));
  String key="training-videos/"+java.util.UUID.randomUUID()+".mp4"; service.put(new MockMultipartFile("file","oss-verification.mp4","video/mp4",bytes),"video/mp4",key);
  try {
   var object=service.open(key);
   try(var input=object.stream()) { assertEquals(bytes.length,object.length());assertArrayEquals(bytes,input.readAllBytes()); }
  } finally { service.delete(key); }
 }
}
