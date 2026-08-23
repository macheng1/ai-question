package com.aiquestion.upload;

import com.aiquestion.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/** 通用文件上传接口，支持单文件和多文件两种调用方式。 */
@Tag(name="通用文件",description="图片、文档等文件上传接口")
@RestController
@RequestMapping("/common")
public class UploadController {
    private final UploadService uploadService;
    public UploadController(UploadService uploadService){this.uploadService=uploadService;}

    @Operation(summary="上传文件",description="files 传一个文件即为单文件上传，传多个文件即为多文件上传。maxSizeMb、maxCount、allowedExtensions 可按业务动态传入并且不能超过系统绝对上限。")
    @PostMapping(value="/upload",consumes= MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<UploadFileResult>> upload(
            @Parameter(description="一个或多个文件") @RequestPart("files") MultipartFile[] files,
            @Parameter(description="OSS 目录，例如 question/images") @RequestParam(required=false,defaultValue="common") String scene,
            @Parameter(description="单个文件大小上限，单位 MB") @RequestParam(required=false) Integer maxSizeMb,
            @Parameter(description="最多上传文件数") @RequestParam(required=false) Integer maxCount,
            @Parameter(description="允许的扩展名，逗号分隔，例如 jpg,png,pdf") @RequestParam(required=false) String allowedExtensions){
        return ApiResponse.ok(uploadService.upload(files,scene,maxSizeMb,maxCount,allowedExtensions));
    }
}
