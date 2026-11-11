package com.aiquestion.storage;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStorageServiceTest {
  private final ObjectStorage aliyun = mock(ObjectStorage.class);
  private final ObjectStorage eds = mock(ObjectStorage.class);
  private FileStorageService service(String provider) {
    when(aliyun.provider()).thenReturn("aliyun");
    when(eds.provider()).thenReturn("eds");
    return new FileStorageService(provider, java.util.List.of(aliyun, eds));
  }
  @Test void newUploadUsesSelectedProviderAndRecordsSource() {
    var storage = service("eds");
    var file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1});
    String key = storage.uploadMaterial(file,"application/pdf","pdf","training-materials");
    assertTrue(key.startsWith("eds:training-materials/"));
    verify(eds).put(eq(file),eq("application/pdf"),eq(key.substring(4)));
    verify(aliyun,never()).put(any(),any(),any());
  }
  @Test void oldMaterialStillReadsAliyunAfterSwitch() {
    var storage = service("eds");
    storage.open("training-materials/old.pdf");
    verify(aliyun).open("training-materials/old.pdf");
  }
  @Test void deletionFollowsRecordedSourceNotCurrentProvider() {
    var storage = service("aliyun");
    storage.delete("eds:training-materials/test.pdf");
    storage.delete("oss:training-videos/test.mp4");
    verify(eds).delete("training-materials/test.pdf");
    verify(aliyun).delete("training-videos/test.mp4");
  }
  @Test void invalidProviderFailsWithoutFallback() {
    assertThrows(IllegalArgumentException.class,()->service("typo"));
    assertThrows(IllegalArgumentException.class,()->service("eds").open("other:file"));
  }
  @Test void publicEndpointCannotReadTrainingKeys() {
    var storage = service("eds");
    assertThrows(com.aiquestion.common.BusinessException.class,
        () -> storage.openPublicFile("eds", "../training-materials/test.pdf"));
    verify(eds, never()).open(any());
  }
  @Test void commonUploadUsesConfiguredPublicApiAddress() {
    var storage = service("eds");
    org.springframework.test.util.ReflectionTestUtils.setField(storage, "publicBaseUrl", "https://example.com/api");
    var file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1});
    var result = storage.upload(file);
    assertTrue(result.url().startsWith("https://example.com/api/common/files/eds/"));
    assertTrue(result.objectKey().startsWith("eds:common-files/"));
  }
}
