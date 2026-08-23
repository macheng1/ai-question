package com.aiquestion.common;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis 通用操作封装，业务代码不直接依赖 StringRedisTemplate。 */
@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class RedisService {
  private final StringRedisTemplate template;
  private final String keyPrefix;

  public RedisService(
      StringRedisTemplate template,
      @Value("${app.redis-key-prefix:aiquestion:}") String keyPrefix) {
    this.template = template;
    this.keyPrefix = keyPrefix;
  }

  private String namespacedKey(String key) {
    return keyPrefix + key;
  }

  public void set(String key, Object value, Duration ttl) {
    template.opsForValue().set(namespacedKey(key), JSONUtil.toJsonStr(value), ttl);
  }

  public String get(String key) {
    return template.opsForValue().get(namespacedKey(key));
  }

  public <T> T get(String key, Class<T> type) {
    var value = get(key);
    return StrUtil.isBlank(value) ? null : JSONUtil.toBean(value, type);
  }

  public Boolean delete(String key) {
    return template.delete(namespacedKey(key));
  }
}
