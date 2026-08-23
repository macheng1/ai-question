package com.aiquestion.security;

import cn.hutool.core.util.StrUtil;
import com.aiquestion.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** JWT 生成和解析统一封装，Controller 不直接操作 JJWT。 */
@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class JwtTokenService {
    private final JwtProperties properties;
    public JwtTokenService(JwtProperties properties){this.properties=properties;validateSecret(properties.secret());validateSecret(properties.refreshSecret());}
    public TokenPair issue(String subject){long accessSeconds=durationSeconds(properties.expiresIn());long refreshSeconds=durationSeconds(properties.refreshExpiresIn());return new TokenPair(create(subject,accessSeconds,properties.secret()),create(subject,refreshSeconds,properties.refreshSecret()),"Bearer",accessSeconds);}
    public String subject(String token,boolean refresh){return Jwts.parser().verifyWith(key(refresh?properties.refreshSecret():properties.secret())).build().parseSignedClaims(token).getPayload().getSubject();}
    private String create(String subject,long seconds,String secret){Instant now=Instant.now();return Jwts.builder().subject(subject).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(seconds))).signWith(key(secret)).compact();}
    private SecretKey key(String secret){return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));}
    private long durationSeconds(String value){if(StrUtil.isBlank(value))return Duration.ofDays(7).toSeconds();char unit=value.charAt(value.length()-1);long number=Long.parseLong(value.substring(0,value.length()-1));return switch(unit){case 'd'->Duration.ofDays(number).toSeconds();case 'h'->Duration.ofHours(number).toSeconds();case 'm'->Duration.ofMinutes(number).toSeconds();default->Long.parseLong(value);};}
    private void validateSecret(String secret){if(StrUtil.isBlank(secret)||secret.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalStateException("JWT secret 至少需要 32 个字符");}
}
