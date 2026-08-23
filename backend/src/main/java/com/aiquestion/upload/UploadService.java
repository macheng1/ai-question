package com.aiquestion.upload;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.UploadProperties;
import com.aiquestion.integration.AliyunOssService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 统一上传业务：动态限制大小、数量和扩展名，底层存储可替换为 OSS/MinIO。 */
@Service
public class UploadService {
    private final UploadProperties properties;
    private final AliyunOssService ossService;
    public UploadService(UploadProperties properties,AliyunOssService ossService){this.properties=properties;this.ossService=ossService;}

    public List<UploadFileResult> upload(MultipartFile[] files,String scene,Integer maxSizeMb,Integer maxCount,String allowedExtensions){
        if(files==null||files.length==0)throw new BusinessException(ErrorCode.UPLOAD_FILE_EMPTY);
        int sizeLimit=resolveLimit(maxSizeMb,properties.defaultMaxSizeMb(),properties.absoluteMaxSizeMb());
        int countLimit=resolveLimit(maxCount,properties.defaultMaxCount(),properties.absoluteMaxCount());
        if(files.length>countLimit)throw new BusinessException(ErrorCode.UPLOAD_FILE_COUNT_EXCEEDED,"最多允许上传 "+countLimit+" 个文件");
        List<String> extensions=parseExtensions(allowedExtensions);
        return Arrays.stream(files).map(file->{validate(file,sizeLimit,extensions);var result=ossService.upload(file,safeScene(scene));return new UploadFileResult(result.objectKey(),result.originalName(),result.url(),result.size(),result.contentType());}).toList();
    }

    private void validate(MultipartFile file,int sizeLimitMb,List<String> extensions){
        if(file==null||file.isEmpty())throw new BusinessException(ErrorCode.UPLOAD_FILE_EMPTY);
        long maxBytes=sizeLimitMb*1024L*1024L;
        if(file.getSize()>maxBytes)throw new BusinessException(ErrorCode.UPLOAD_FILE_TOO_LARGE,"单个文件最大允许 "+sizeLimitMb+" MB");
        String ext=FileNameUtil.extName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if(!extensions.isEmpty()&&!extensions.contains(ext))throw new BusinessException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED,"不允许上传 ."+ext+" 文件");
    }
    private List<String> parseExtensions(String value){
        if(StrUtil.isBlank(value))return properties.defaultAllowedExtensions()==null?List.of():properties.defaultAllowedExtensions().stream().map(x->x.toLowerCase(Locale.ROOT).replace(".","")).toList();
        return StrUtil.splitTrim(value,',').stream().map(x->x.toLowerCase(Locale.ROOT).replace(".","")).filter(StrUtil::isNotBlank).toList();
    }
    private int resolveLimit(Integer requested,int fallback,int absolute){
        int value=requested==null?fallback:requested;
        if(value<=0||value>absolute)throw new BusinessException(ErrorCode.BAD_REQUEST,"上传限制参数超出允许范围，最大值为 "+absolute);
        return value;
    }
    private String safeScene(String scene){String value=StrUtil.blankToDefault(scene,"common");return ReUtil.replaceAll(value,"[^a-zA-Z0-9/_-]","_");}
}
