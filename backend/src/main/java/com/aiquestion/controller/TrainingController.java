package com.aiquestion.controller;

import com.aiquestion.common.*;
import com.aiquestion.security.JwtTokenService;
import com.aiquestion.service.TrainingService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class TrainingController {
  private final TrainingService service;
  private final JwtTokenService tokens;
  private final JdbcTemplate jdbc;
  public TrainingController(TrainingService service, JwtTokenService tokens, JdbcTemplate jdbc) { this.service = service; this.tokens = tokens; this.jdbc = jdbc; }
  private long user(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    long id;
    try { id = Long.parseLong(tokens.subject(authorization.substring(7), false)); }
    catch (RuntimeException e) { throw new BusinessException(ErrorCode.INVALID_TOKEN); }
    if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND status='ACTIVE'", Integer.class, id) == 0) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    return id;
  }
  @GetMapping("/management/trainings")
  public ApiResponse<?> list(@RequestHeader(value="Authorization", required=false) String auth) { return ApiResponse.ok(service.list(user(auth))); }
  @GetMapping("/management/trainings/{id}")
  public ApiResponse<?> get(@PathVariable long id, @RequestHeader(value="Authorization", required=false) String auth) { return ApiResponse.ok(service.get(id, user(auth))); }
  @PostMapping("/management/trainings")
  public ApiResponse<?> create(@RequestBody TrainingService.SaveTraining command, @RequestHeader(value="Authorization", required=false) String auth) { return ApiResponse.ok(service.save(null, command, user(auth))); }
  @PutMapping("/management/trainings/{id}")
  public ApiResponse<?> update(@PathVariable long id, @RequestBody TrainingService.SaveTraining command, @RequestHeader(value="Authorization", required=false) String auth) { return ApiResponse.ok(service.save(id, command, user(auth))); }
  @DeleteMapping("/management/trainings/{id}")
  public ApiResponse<?> delete(@PathVariable long id, @RequestHeader(value="Authorization", required=false) String auth) { long uid=user(auth); var keys=service.videoFiles(id,uid); service.delete(id,uid); keys.forEach(this::cleanupVideo); return ApiResponse.ok(null); }
  @GetMapping("/management/trainings/{id}/responses")
  public ApiResponse<?> responses(@PathVariable long id, @RequestHeader(value="Authorization", required=false) String auth) { return ApiResponse.ok(service.responses(id, user(auth))); }
  @GetMapping("/trainings/{id}/survey")
  public ApiResponse<?> survey(@PathVariable long id, @RequestHeader(value="Authorization", required=false) String auth) { return ApiResponse.ok(service.survey(id, user(auth))); }
  @PostMapping("/trainings/{id}/survey")
  public ApiResponse<?> submit(@PathVariable long id, @RequestBody TrainingService.Submission command, @RequestHeader(value="Authorization", required=false) String auth) { service.submit(id, command, user(auth)); return ApiResponse.ok(null); }
  @org.springframework.beans.factory.annotation.Value("${training.video-directory:./data/training-videos}")
  private String videoDirectory;
  @GetMapping("/trainings/{id}")
  public ApiResponse<?> learning(@PathVariable long id,@RequestHeader(value="Authorization",required=false) String auth) { return ApiResponse.ok(service.learning(id,user(auth))); }
  public record Progress(double position,boolean completed) {}
  @PutMapping("/trainings/{id}/videos/{video}/progress")
  public ApiResponse<?> progress(@PathVariable long id,@PathVariable long video,@RequestBody Progress data,@RequestHeader(value="Authorization",required=false) String auth) {service.progress(id,video,user(auth),data.position(),data.completed());return ApiResponse.ok(null);}
  @PostMapping("/management/trainings/{id}/videos")
  public ApiResponse<?> upload(@PathVariable long id,@RequestParam("file") org.springframework.web.multipart.MultipartFile file,@RequestHeader(value="Authorization",required=false) String auth) throws java.io.IOException {
    long uid=user(auth); service.get(id,uid);
    String name=Objects.toString(file.getOriginalFilename(),"");
    boolean mp4=name.toLowerCase(Locale.ROOT).endsWith(".mp4"),webm=name.toLowerCase(Locale.ROOT).endsWith(".webm");
    if((!mp4&&!webm)||file.getSize()<12||file.getSize()>100L*1024*1024) throw new BusinessException(ErrorCode.BAD_REQUEST,"仅支持100MB以内的MP4或WebM视频");
    byte[] head;try(var stream=file.getInputStream()){head=stream.readNBytes(12);}
    boolean valid=mp4 ? head[4]=='f'&&head[5]=='t'&&head[6]=='y'&&head[7]=='p' : (head[0]&255)==0x1a&&(head[1]&255)==0x45&&(head[2]&255)==0xdf&&(head[3]&255)==0xa3;
    if(!valid) throw new BusinessException(ErrorCode.BAD_REQUEST,"视频文件内容与扩展名不匹配");
    var root=java.nio.file.Path.of(videoDirectory).toAbsolutePath().normalize(); java.nio.file.Files.createDirectories(root);
    String key=UUID.randomUUID()+(mp4?".mp4":".webm"); var target=root.resolve(key);
    try(var stream=file.getInputStream()){java.nio.file.Files.copy(stream,target);}
    try{service.addVideo(id,uid,name.substring(0,Math.min(name.length(),255)),key,mp4?"video/mp4":"video/webm");}catch(RuntimeException e){java.nio.file.Files.deleteIfExists(target);throw e;}
    return ApiResponse.ok(service.learning(id,uid));
  }
  @GetMapping("/trainings/{id}/videos/{video}/content")
  public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> content(@PathVariable long id,@PathVariable long video,@RequestHeader(value="Authorization",required=false) String auth) {
    user(auth);var row=service.video(id,video);var root=java.nio.file.Path.of(videoDirectory).toAbsolutePath().normalize();var path=root.resolve(row.get("storage_path").toString()).normalize();
    if(!path.startsWith(root)||!java.nio.file.Files.isRegularFile(path)) throw new BusinessException(ErrorCode.NOT_FOUND);
    return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(row.get("content_type").toString())).header("Cache-Control","private, no-store").body(new org.springframework.core.io.FileSystemResource(path));
  }
  @DeleteMapping("/management/trainings/{id}/videos/{video}")
  public ApiResponse<?> removeVideo(@PathVariable long id,@PathVariable long video,@RequestHeader(value="Authorization",required=false) String auth) {
    String key=service.removeVideo(id,video,user(auth));
    cleanupVideo(key);
    return ApiResponse.ok(null);
  }
  @PutMapping("/management/trainings/{id}/videos/order")
  public ApiResponse<?> reorderVideos(@PathVariable long id,@RequestBody List<Long> ids,@RequestHeader(value="Authorization",required=false) String auth) {
    service.reorderVideos(id,user(auth),ids);return ApiResponse.ok(null);
  }
  private void cleanupVideo(String key) {
    var root=java.nio.file.Path.of(videoDirectory).toAbsolutePath().normalize();var path=root.resolve(key).normalize();
    if(path.startsWith(root)) try{java.nio.file.Files.deleteIfExists(path);}catch(java.io.IOException error){
      org.slf4j.LoggerFactory.getLogger(TrainingController.class).warn("Training video cleanup failed for storage key {}",key,error);
    }
  }
}
