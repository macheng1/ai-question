package com.aiquestion.integration;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.AliyunSmsProperties;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.profile.DefaultProfile;
import org.springframework.stereotype.Service;

/** 阿里云短信适配层，业务服务不直接依赖阿里云 SDK。 */
@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class AliyunSmsService {
  private final AliyunSmsProperties properties;

  public AliyunSmsService(AliyunSmsProperties properties) {
    this.properties = properties;
  }

  public void sendCode(String phone, String code) {
    if (properties.accessKeyId() == null || properties.accessKeyId().isBlank()) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "阿里云短信配置未完成");
    }
    try {
      DefaultProfile.addEndpoint(
          "", "cn-hangzhou", "Dysmsapi", properties.endpoint().replace("https://", ""));
      DefaultProfile profile =
          DefaultProfile.getProfile(
              "cn-hangzhou", properties.accessKeyId(), properties.accessKeySecret());
      IAcsClient client = new DefaultAcsClient(profile);
      SendSmsRequest request = new SendSmsRequest();
      request.setPhoneNumbers(phone);
      request.setSignName(properties.signName());
      request.setTemplateCode(properties.templateCode());
      request.setTemplateParam("{\"code\":\"" + code + "\"}");
      SendSmsResponse response = client.getAcsResponse(request);
      if (!"OK".equalsIgnoreCase(response.getCode())) {
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "短信发送失败：" + response.getMessage());
      }
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BusinessException(ErrorCode.SYSTEM_ERROR, "短信服务调用失败");
    }
  }
}
