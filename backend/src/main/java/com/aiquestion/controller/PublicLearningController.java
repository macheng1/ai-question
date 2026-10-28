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
 private final com.aiquestion.service.TrainingVideoStorage storage;
 public PublicLearningController(CandidateLearningService service,JdbcTemplate jdbc,com.aiquestion.service.TrainingVideoStorage storage) {this.service=service;this.jdbc=jdbc;this.storage=storage;}
 @GetMapping("/exams/{id}/trainings")
 public ApiResponse<List<Map<String,Object>>> trainings(@PathVariable long id) { return ApiResponse.ok(service.trainings(id)); }
 @GetMapping("/trainings/{id}/exam")
 public ApiResponse<Map<String,Object>> exam(@PathVariable long id) {
  var rows=jdbc.queryForList("SELECT assessment_id assessmentId FROM training WHERE id=?",id);
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
 public record ProgressCommand(String token,double position,double duration,boolean completed) {}
}
