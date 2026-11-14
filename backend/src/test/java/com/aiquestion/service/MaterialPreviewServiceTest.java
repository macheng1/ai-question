package com.aiquestion.service;

import com.aiquestion.repository.MaterialPreviewRepository;
import com.aiquestion.storage.FileStorageService;
import com.aiquestion.storage.ObjectStorage;
import java.io.*;
import java.util.*;
import org.apache.ibatis.session.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterialPreviewServiceTest {
  @Test void resumedPdfSkipsCompletedPagesAndReusesReservedKey() throws Exception {
    var repository=mock(MaterialPreviewRepository.class);
    var factory=mock(SqlSessionFactory.class);
    var session=mock(SqlSession.class);
    var locks=mock(MaterialPreviewRepository.class);
    var storage=mock(FileStorageService.class);
    when(factory.openSession(true)).thenReturn(session);
    when(session.getMapper(MaterialPreviewRepository.class)).thenReturn(locks);
    when(locks.acquire(1)).thenReturn(1);
    when(repository.claim(1)).thenReturn(1);
    when(repository.material(1)).thenReturn(Map.of("storage_key","eds:original.pdf","material_type","PDF"));
    when(repository.pages(1)).thenReturn(List.of(
      Map.of("page_number",1,"preview_ready",true,"preview_key","eds:first.png"),
      Map.of("page_number",2,"preview_ready",false,"preview_key","eds:reserved.png")));
    byte[] pdfBytes;
    try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()) {
      pdf.addPage(new PDPage());pdf.addPage(new PDPage());pdf.save(out);pdfBytes=out.toByteArray();
    }
    when(storage.open("eds:original.pdf")).thenReturn(new ObjectStorage.StoredObject(new ByteArrayInputStream(pdfBytes),pdfBytes.length,"application/pdf"));
    new MaterialPreviewService(repository,factory,storage).process(1);
    verify(storage).putPreview(eq("eds:reserved.png"),argThat(bytes -> bytes.length>0));
    verify(storage,never()).reservePreview(anyString());
    verify(repository,never()).pageReady(1,1);
    verify(repository).pageReady(1,2);
    verify(repository).ready(1);
    verify(locks).release(1);
    verify(session).close();
  }
}
