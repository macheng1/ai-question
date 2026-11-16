package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class VideoPlaybackTickets {
    private static final Duration LIFETIME = Duration.ofHours(2);
    private final SecretKey key;

    public VideoPlaybackTickets(JwtProperties properties) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        key = new SecretKeySpec(mac.doFinal("training-video-playback-v1".getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
    }

    public String issue(long training, long video) {
        return issue(training, video, Clock.systemUTC());
    }

    String issue(long training, long video, Clock clock) {
        return Jwts.builder().subject(Long.toString(video)).claim("training", training)
            .expiration(Date.from(clock.instant().plus(LIFETIME))).signWith(key).compact();
    }

    public long verify(long video, String ticket) {
        try {
            var claims = Jwts.parser().verifyWith(key).requireSubject(Long.toString(video)).build()
                .parseSignedClaims(ticket).getPayload();
            return claims.get("training", Long.class);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "视频播放凭证无效或已过期，请重新加载");
        }
    }
}
