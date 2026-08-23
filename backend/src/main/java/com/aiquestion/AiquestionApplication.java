package com.aiquestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SuppressWarnings("AlibabaJavaCommentsClassMustHaveAuthorRule")
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class AiquestionApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiquestionApplication.class, args);
  }
}
