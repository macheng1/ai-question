package com.aiquestion.service;

import com.aiquestion.repository.MaterialPreviewRepository;
import com.aiquestion.storage.FileStorageService;
import java.io.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

/** Durable, resumable conversion. No database transaction is held while rendering or uploading. */
@Service
public class MaterialPreviewService {
  private final MaterialPreviewRepository repository;
  private final SqlSessionFactory sessions;
  private final FileStorageService storage;
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MaterialPreviewService.class);

  public MaterialPreviewService(MaterialPreviewRepository repository, SqlSessionFactory sessions, FileStorageService storage) {
    this.repository=repository; this.sessions=sessions; this.storage=storage;
  }

  public void processPending() {
    for (long id : repository.pending()) {
      if (Thread.currentThread().isInterrupted()) return;
      process(id);
    }
  }

  public void process(long id) {
    try (var session = sessions.openSession(true)) {
      var lock = session.getMapper(MaterialPreviewRepository.class);
      if (!Integer.valueOf(1).equals(lock.acquire(id))) return;
      try {
        if (repository.claim(id)==0) return;
        convert(id);
        repository.ready(id);
      } catch (Exception e) {
        repository.failed(id);
        log.error("Material preview conversion failed materialId={}",id,e);
      } finally {
        lock.release(id);
      }
    }
  }

  private void convert(long id) throws IOException {
    var material=repository.material(id);
    String original=String.valueOf(material.get("storage_key"));
    Path temp=Files.createTempFile("training-preview-", ".source");
    try {
      try (var input=storage.open(original).stream()) { Files.copy(input,temp,StandardCopyOption.REPLACE_EXISTING); }
      if ("PDF".equals(material.get("material_type"))) {
        try (var document=Loader.loadPDF(temp.toFile())) {
          var renderer=new PDFRenderer(document);
          renderer.setSubsamplingAllowed(true);
          for (var page : repository.pages(id)) {
            if (ready(page.get("preview_ready"))) continue;
            int number=((Number)page.get("page_number")).intValue();
            var box=document.getPage(number-1).getCropBox();
            float scale=Math.min(1.5f,1600f/Math.max(box.getWidth(),box.getHeight()));
            if (!Float.isFinite(scale)||scale<=0) throw new IOException("Invalid PDF page size");
            save(id,number,original,page.get("preview_key"),renderer.renderImage(number-1,scale));
          }
        }
      } else {
        var page=repository.pages(id).get(0);
        if (!ready(page.get("preview_ready"))) {
          BufferedImage source=ImageIO.read(temp.toFile());
          if (source==null) throw new IOException("Invalid image");
          double scale=Math.min(1d,1600d/Math.max(source.getWidth(),source.getHeight()));
          BufferedImage result=new BufferedImage(Math.max(1,(int)(source.getWidth()*scale)),Math.max(1,(int)(source.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
          var graphics=result.createGraphics();
          try { graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,result.getWidth(),result.getHeight()); graphics.drawImage(source,0,0,result.getWidth(),result.getHeight(),null); }
          finally { graphics.dispose(); source.flush(); }
          save(id,1,original,page.get("preview_key"),result);
        }
      }
    } finally { Files.deleteIfExists(temp); }
  }

  private static boolean ready(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()!=0; }

  private void save(long id,int page,String original,Object reserved,BufferedImage image) throws IOException {
    try {
      String key=reserved==null ? storage.reservePreview(original) : reserved.toString();
      if (reserved==null) repository.reserve(id,page,key);
      try (var output=new ByteArrayOutputStream()) {
        if (!ImageIO.write(image,"png",output)) throw new IOException("PNG encoder unavailable");
        storage.putPreview(key,output.toByteArray());
      }
      repository.pageReady(id,page);
    } finally { image.flush(); }
  }
}
