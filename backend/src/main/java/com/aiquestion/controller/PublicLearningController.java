package com.aiquestion.controller;
import com.aiquestion.common.*;
import com.aiquestion.service.CandidateLearningService;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.core.io.*;
import java.util.*;
@RestController
@RequestMapping("/public")
public class PublicLearningController {
 private final CandidateLearningService service;
 private final JdbcTemplate jdbc;
 private final com.aiquestion.service.TrainingMaterialService materials;
 private final com.aiquestion.service.MaterialLearningService reading;
 private final com.aiquestion.service.TrainingVideoStorage storage;
 public PublicLearningController(CandidateLearningService service,JdbcTemplate jdbc,com.aiquestion.service.TrainingVideoStorage storage,com.aiquestion.service.TrainingMaterialService materials,com.aiquestion.service.MaterialLearningService reading) {this.reading=reading;this.materials=materials;this.service=service;this.jdbc=jdbc;this.storage=storage;}
 @PostMapping("/courses/{id}/complete")
 public ApiResponse<?> courseComplete(@PathVariable long id,@RequestBody Map<String,String> command){if(id<=0)throw new BusinessException(ErrorCode.BAD_REQUEST);var identity=reading.session(-id,command.get("token"));service.requireComplete(-id,identity.get("candidate_name").toString(),identity.get("phone").toString());return ApiResponse.ok(Map.of("completed",true));}
 @PostMapping("/courses/{id}/learning")
 public ApiResponse<Map<String,Object>> courseLearning(@PathVariable long id,@RequestBody PublicExamController.CandidateCommand command){if(id<=0)throw new BusinessException(ErrorCode.BAD_REQUEST);return ApiResponse.ok(service.learning(-id,command.name(),command.phone()));}
 @GetMapping("/courses/{id}/learning/videos/{video}/content")
 public ResponseEntity<Resource> courseVideo(@PathVariable long id,@PathVariable long video,@RequestParam String token){if(id<=0)throw new BusinessException(ErrorCode.BAD_REQUEST);return storage.content(service.video(-id,video,token));}
 @PutMapping("/courses/{id}/learning/videos/{video}/progress")
 public ApiResponse<?> courseVideoProgress(@PathVariable long id,@PathVariable long video,@RequestBody ProgressCommand c){if(id<=0)throw new BusinessException(ErrorCode.BAD_REQUEST);return ApiResponse.ok(service.progress(-id,video,c.token(),c.position(),c.duration(),c.completed()));}
 @GetMapping(value="/courses/{id}/learning/materials/{material}/pages/{page}",produces="image/png")
 public ResponseEntity<byte[]> coursePage(@PathVariable long id,@PathVariable long material,@PathVariable int page,@RequestParam String token) throws java.io.IOException {if(id<=0)throw new BusinessException(ErrorCode.BAD_REQUEST);reading.authorize(-id,material,token,page);return ResponseEntity.ok().header("Cache-Control","private, no-store").body(materials.page(id,material,page));}
 @PutMapping("/courses/{id}/learning/materials/{material}/pages/{page}/progress")
 public ApiResponse<?> coursePageProgress(@PathVariable long id,@PathVariable long material,@PathVariable int page,@RequestBody Reading c){if(id<=0)throw new BusinessException(ErrorCode.BAD_REQUEST);return ApiResponse.ok(reading.heartbeat(-id,material,page,c.token(),c.heartbeatToken(),c.seconds()));}
 @GetMapping("/exams/{id}/trainings")
 public ApiResponse<List<Map<String,Object>>> trainings(@PathVariable long id) { return ApiResponse.ok(service.trainings(id)); }
 @GetMapping("/trainings/{id}/exam")
 public ApiResponse<Map<String,Object>> exam(@PathVariable long id) {
  var rows=jdbc.queryForList("SELECT purpose,assessment_id assessmentId,name,description,category,requirement_type requirementType,survey_enabled surveyEnabled FROM training WHERE id=?",id);
  if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"培训不存在");
  return ApiResponse.ok(rows.get(0));
 }
 @PostMapping("/exams/{id}/learning")
 public ApiResponse<Map<String,Object>> learning(@PathVariable long id,@RequestBody PublicExamController.CandidateCommand command) {return ApiResponse.ok(service.learning(id,command.name(),command.phone()));}
 @GetMapping("/exams/{id}/learning/videos/{video}/content")
 public ResponseEntity<Resource> content(@PathVariable long id,@PathVariable long video,@RequestParam String token) {
  var row=service.video(id,video,token);
  return storage.content(row);
 }
 @PutMapping("/exams/{id}/learning/videos/{video}/progress")
 public ApiResponse<Map<String,Object>> progress(@PathVariable long id,@PathVariable long video,@RequestBody ProgressCommand command) { return ApiResponse.ok(service.progress(id,video,command.token(),command.position(),command.duration(),command.completed())); }
 @GetMapping(value="/exams/{id}/learning/materials/{material}/pages/{page}",produces="image/png")
 public ResponseEntity<byte[]> page(@PathVariable long id,@PathVariable long material,@PathVariable int page,@RequestParam String token) throws java.io.IOException {
  reading.authorize(id,material,token,page);
  long training=jdbc.queryForObject("SELECT training_id FROM training_material WHERE id=?",Long.class,material);
  return ResponseEntity.ok().header("Cache-Control","private, no-store").body(materials.page(training,material,page));
 }
 @PutMapping("/exams/{id}/learning/materials/{material}/pages/{page}/progress")
 public ApiResponse<?> pageProgress(@PathVariable long id,@PathVariable long material,@PathVariable int page,@RequestBody Reading command) {return ApiResponse.ok(reading.heartbeat(id,material,page,command.token(),command.heartbeatToken(),command.seconds()));}
 public record Reading(String token,String heartbeatToken,double seconds) {}
 public record ProgressCommand(String token,double position,double duration,boolean completed) {}
}
