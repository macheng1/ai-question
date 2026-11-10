package com.aiquestion.security;

import cn.hutool.core.util.StrUtil;
import com.aiquestion.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Signs typed identities. Numeric subjects remain compatible with existing administrator tokens. */
@Service
public class JwtTokenService {
  private final JwtProperties properties;

  public JwtTokenService(JwtProperties properties) {
    this.properties = properties;
    validateSecret(properties.secret());
    validateSecret(properties.refreshSecret());
  }

  public TokenPair issue(String administratorId) {
    long id = Long.parseLong(administratorId);
    return issue(new AccountIdentity(id, AccountType.ADMIN, id));
  }

  public TokenPair issue(AccountIdentity identity) {
    long accessSeconds = durationSeconds(properties.expiresIn());
    long refreshSeconds = durationSeconds(properties.refreshExpiresIn());
    return new TokenPair(create(identity, accessSeconds, properties.secret()),
        create(identity, refreshSeconds, properties.refreshSecret()), "Bearer", accessSeconds);
  }

  public String subject(String token, boolean refresh) {
    Claims claims = Jwts.parser().verifyWith(key(refresh ? properties.refreshSecret() : properties.secret()))
        .build().parseSignedClaims(token).getPayload();
    String subject = claims.getSubject();
    String type = claims.get("accountType", String.class);
    // Pre-upgrade tokens had no accountType and could only represent administrator accounts.
    if (type == null && subject.startsWith("employee:")) throw new IllegalArgumentException("Missing account type");
    if (type != null) {
      boolean employee = AccountType.valueOf(type) == AccountType.EMPLOYEE;
      if (employee != subject.startsWith("employee:")) throw new IllegalArgumentException("Account type mismatch");
    }
    return subject;
  }

  private String create(AccountIdentity identity, long seconds, String secret) {
    Instant now = Instant.now();
    return Jwts.builder().subject(identity.subject()).claim("accountType", identity.accountType().name())
        .claim("enterpriseId", identity.enterpriseId()).issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(seconds))).signWith(key(secret)).compact();
  }

  private SecretKey key(String secret) { return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }

  private long durationSeconds(String value) {
    if (StrUtil.isBlank(value)) return Duration.ofDays(7).toSeconds();
    char unit = value.charAt(value.length() - 1);
    if (Character.isDigit(unit)) return Long.parseLong(value);
    long number = Long.parseLong(value.substring(0, value.length() - 1));
    return switch (unit) {
      case 'd' -> Duration.ofDays(number).toSeconds();
      case 'h' -> Duration.ofHours(number).toSeconds();
      case 'm' -> Duration.ofMinutes(number).toSeconds();
      default -> throw new IllegalArgumentException("Unsupported JWT duration");
    };
  }

  private void validateSecret(String secret) {
    if (StrUtil.isBlank(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalStateException("JWT secret 至少需要 32 个字符");
  }
}
