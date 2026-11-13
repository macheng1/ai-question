package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.TrainingCopyService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class TrainingCopyController {
  private final TrainingCopyService service;

  public TrainingCopyController(TrainingCopyService service) { this.service = service; }

  @PostMapping("/management/trainings/{id}/copy")
  public ApiResponse<?> copy(@PathVariable long id, @RequestBody TrainingCopyService.CopyRequest request) {
    if (request != null && request.training() != null) request.training().requireSchedule();
    return ApiResponse.ok(service.copyAndSave(id, ManagementAccess.userId(), request));
  }

  @PostMapping(value = "/management/trainings/{id}/copy-with-materials", consumes = "multipart/form-data")
  public ApiResponse<?> copyDraft(@PathVariable long id,
      @org.springframework.web.bind.annotation.RequestPart("draft") TrainingCopyService.DraftRequest draft,
      @org.springframework.web.bind.annotation.RequestPart(value = "files", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> files,
      @org.springframework.web.bind.annotation.RequestHeader("Authorization") String auth) throws java.io.IOException {
    if (draft == null || draft.copy() == null || draft.copy().training() == null || draft.items() == null)
      throw new com.aiquestion.common.BusinessException(com.aiquestion.common.ErrorCode.BAD_REQUEST, "请确认复制表单后保存");
    draft.copy().training().requireSchedule();
    return ApiResponse.ok(service.copyDraft(id, ManagementAccess.userId(), draft.copy(), draft.items(), files == null ? java.util.List.of() : files, auth));
  }
}
