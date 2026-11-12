package com.aiquestion.service;

import com.aiquestion.storage.FileStorageService;
import com.aiquestion.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrainingFileCopyServiceTest {
  @Test void materialHasIndependentObjectAndCanBeDeletedWithoutRemovingOriginal() throws Exception {
    var provider = mock(ObjectStorage.class);
    when(provider.provider()).thenReturn("aliyun");
    var storage = new FileStorageService("aliyun", List.of(provider));
    Map<String, byte[]> objects = new HashMap<>();
    byte[] original = "example-pdf-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    objects.put("training-materials/original.pdf", original);
    when(provider.open(anyString())).thenAnswer(call -> {
      byte[] data = objects.get(call.getArgument(0));
      return new ObjectStorage.StoredObject(new ByteArrayInputStream(data), data.length, "application/pdf");
    });
    doAnswer(call -> {
      MultipartFile file = call.getArgument(0);
      objects.put(call.getArgument(2), file.getBytes());
      return null;
    }).when(provider).put(any(), anyString(), anyString());
    doAnswer(call -> { objects.remove(call.getArgument(0)); return null; }).when(provider).delete(anyString());
    var files = new TrainingFileCopyService(storage, mock(TrainingVideoStorage.class));
    String copied = files.copyMaterial(Map.of("storage_key", "oss:training-materials/original.pdf", "material_type", "PDF"));
    assertNotEquals("oss:training-materials/original.pdf", copied);
    assertArrayEquals(original, objects.get(copied.substring(4)));
    storage.delete(copied);
    assertArrayEquals(original, objects.get("training-materials/original.pdf"));
    assertEquals(1, objects.size());
  }
}
