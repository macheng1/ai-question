package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.storage.FileStorageService;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TrainingMaterialService {
 private final JdbcTemplate jdbc;
 private final FileStorageService oss;
 private final TrainingService trainings;
 public TrainingMaterialService(JdbcTemplate jdbc, FileStorageService oss, TrainingService trainings) { this.jdbc=jdbc; this.oss=oss; this.trainings=trainings; }
 public List<Map<String,Object>> list(long training) {
  return jdbc.queryForList("SELECT id,title,material_type materialType,page_count pageCount,sort_order sortOrder FROM training_material WHERE training_id=? ORDER BY sort_order,id",training).stream().map(r->{r.put("pages",jdbc.queryForList("SELECT page_number pageNumber,required_seconds requiredSeconds FROM training_material_page WHERE material_id=? ORDER BY page_number",r.get("id")));return r;}).toList();
 }
 public Map<String,Object> material(long training,long id) {
  var rows=jdbc.queryForList("SELECT * FROM training_material WHERE id=? AND training_id=?",id,training);
  if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"材料不存在");
  return rows.get(0);
 }
 @Transactional
 public void upload(long training,long user,MultipartFile file) throws IOException {
  trainings.get(training,user);
  jdbc.queryForMap("SELECT id FROM training WHERE id=? FOR UPDATE",training);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_learning_session s JOIN training t ON (t.assessment_id=s.task_id OR t.id=s.training_id) WHERE t.id=?",Integer.class,training)>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"已有学习记录，不能新增材料，请新建培训");
  if(file.isEmpty()||file.getSize()>20*1024*1024) throw new BusinessException(ErrorCode.BAD_REQUEST,"PDF和图片不能超过20MB");
  byte[] bytes=file.getBytes(); String title=Objects.toString(file.getOriginalFilename(),"材料");
  if(title.length()>255) throw new BusinessException(ErrorCode.BAD_REQUEST,"文件名过长");
  String suffix=title.toLowerCase(Locale.ROOT); String type,mime,extension; int pages=1;
  if(suffix.endsWith(".pdf")) {
   try(var document=Loader.loadPDF(bytes)) { pages=document.getNumberOfPages(); if(document.isEncrypted()||pages<1||pages>200) throw new BusinessException(ErrorCode.BAD_REQUEST,"请上传未加密且不超过200页的PDF"); }
   type="PDF";mime="application/pdf";extension="pdf";
  } else {
   if(!suffix.endsWith(".png")&&!suffix.endsWith(".jpg")&&!suffix.endsWith(".jpeg")) throw new BusinessException(ErrorCode.BAD_REQUEST,"仅支持PDF、PNG、JPG");
   try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
    var readers=ImageIO.getImageReaders(input); if(!readers.hasNext()) throw new BusinessException(ErrorCode.BAD_REQUEST,"图片内容无效");
    var reader=readers.next(); try { reader.setInput(input); if((long)reader.getWidth(0)*reader.getHeight(0)>40_000_000) throw new BusinessException(ErrorCode.BAD_REQUEST,"图片尺寸过大"); String format=reader.getFormatName(); if(!Set.of("png","jpeg","jpg").contains(format.toLowerCase(Locale.ROOT)))throw new BusinessException(ErrorCode.BAD_REQUEST,"图片内容必须为PNG或JPG"); extension=format.equalsIgnoreCase("png")?"png":"jpg"; } finally {reader.dispose();}
   }
   type="IMAGE";mime="png".equals(extension)?"image/png":"image/jpeg";
  }
  String key=oss.uploadMaterial(file,mime,extension,"training-materials");
  try {
   jdbc.update("INSERT INTO training_material(training_id,title,material_type,storage_key,page_count) VALUES(?,?,?,?,?)",training,title,type,key,pages);
   Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
   for(int page=1;page<=pages;page++) jdbc.update("INSERT INTO training_material_page(material_id,page_number) VALUES(?,?)",id,page);
  } catch(RuntimeException e) {try {oss.delete(key);} catch(RuntimeException ignored) {} throw e;}
 }
 public byte[] page(long training,long id,int page) throws IOException {
  var row=material(training,id); if(page<1||page>((Number)row.get("page_count")).intValue()) throw new BusinessException(ErrorCode.BAD_REQUEST,"页码无效");
  var object=oss.open(String.valueOf(row.get("storage_key")));
  byte[] bytes; try(var input=object.stream()) {bytes=input.readAllBytes();}
  if("PDF".equals(row.get("material_type"))) {
   try(var document=Loader.loadPDF(bytes);var out=new ByteArrayOutputStream()) {
    var box=document.getPage(page-1).getCropBox(); float scale=Math.min(1.5f,1600f/Math.max(box.getWidth(),box.getHeight()));
    if(!Float.isFinite(scale)||scale<=0) throw new BusinessException(ErrorCode.BAD_REQUEST,"PDF页面尺寸无效");
    var renderer=new PDFRenderer(document);renderer.setSubsamplingAllowed(true);
    ImageIO.write(renderer.renderImage(page-1,scale),"png",out);return out.toByteArray();
   }
  }
  try(var out=new ByteArrayOutputStream()) {ImageIO.write(ImageIO.read(new ByteArrayInputStream(bytes)),"png",out);return out.toByteArray();}
 }
 @Transactional public void reorder(long training,long user,List<Long> ids) {
  trainings.get(training,user);jdbc.queryForMap("SELECT id FROM training WHERE id=? FOR UPDATE",training);
  var existing=jdbc.queryForList("SELECT id FROM training_material WHERE training_id=?",Long.class,training);
  if(ids==null||ids.size()!=existing.size()||new HashSet<>(ids).size()!=ids.size()||!new HashSet<>(ids).equals(new HashSet<>(existing)))throw new BusinessException(ErrorCode.BAD_REQUEST,"材料列表已变更，请刷新后重试");
  for(int i=0;i<ids.size();i++)jdbc.update("UPDATE training_material SET sort_order=? WHERE id=? AND training_id=?",i,ids.get(i),training);
 }
 public record PageTime(int pageNumber,int requiredSeconds) {}
 @Transactional
 public void timings(long training,long id,long user,List<PageTime> times) {
  trainings.get(training,user); jdbc.queryForMap("SELECT id FROM training_material WHERE id=? AND training_id=? FOR UPDATE",id,training); var row=material(training,id);
  if(times==null||times.size()!=((Number)row.get("page_count")).intValue()||times.stream().map(PageTime::pageNumber).distinct().count()!=times.size()) throw new BusinessException(ErrorCode.BAD_REQUEST,"请设置每一页的时长");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_material_progress WHERE material_id=?",Integer.class,id)>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"已有学习记录，不能修改阅读时长");
  for(var t:times) {if(t.pageNumber()<1||t.pageNumber()>times.size()||t.requiredSeconds()<1||t.requiredSeconds()>3600) throw new BusinessException(ErrorCode.BAD_REQUEST,"阅读时长应为1至3600秒");jdbc.update("UPDATE training_material_page SET required_seconds=? WHERE material_id=? AND page_number=?",t.requiredSeconds(),id,t.pageNumber());}
 }
 @Transactional
 public void delete(long training,long id,long user) {
  trainings.get(training,user);jdbc.queryForMap("SELECT id FROM training_material WHERE id=? AND training_id=? FOR UPDATE",id,training);var row=material(training,id);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_material_progress WHERE material_id=?",Integer.class,id)>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"已有学习记录，不能删除材料");
  jdbc.update("DELETE FROM training_material WHERE id=?",id);
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){try{oss.delete(String.valueOf(row.get("storage_key")));}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(TrainingMaterialService.class).warn("材料对象清理失败 id={}",id,e);}}});
 }
}
