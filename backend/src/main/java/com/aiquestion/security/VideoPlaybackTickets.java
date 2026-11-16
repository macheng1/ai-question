package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.ExpiredJwtException;
import com.aiquestion.common.VideoLog;
import com.aiquestion.common.TraceContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(VideoPlaybackTickets.class);
    public enum Source { ADMIN_PREVIEW, LEARNER_REVIEW, COURSE_LEARNING, EXAM_LEARNING, LEGACY }
    public record Grant(long training, Source source) {}

    private static final Duration LIFETIME = Duration.ofHours(2);
    private final SecretKey key;

    public VideoPlaybackTickets(JwtProperties properties) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        key = new SecretKeySpec(mac.doFinal("training-video-playback-v1".getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
    }

    public String issue(long training, long video, Source source) {
        return issue(training, video, source, Clock.systemUTC());
    }

    String issue(long training, long video, Source source, Clock clock) {
        String ticket = Jwts.builder().id(UUID.randomUUID().toString()).subject(Long.toString(video))
            .claim("training", training).claim("source", source.name())
            .expiration(Date.from(clock.instant().plus(LIFETIME))).signWith(key).compact();
        log.info("video event=playback_ticket_issued source={} training={} video={} playbackRef={} ttlSeconds={} traceId={}",
            source, training, video, VideoLog.id(ticket), LIFETIME.toSeconds(), TraceContext.get());
        return ticket;
    }

    public Grant verify(long video, String ticket) {
        try {
            var claims = Jwts.parser().verifyWith(key).requireSubject(Long.toString(video)).build()
                .parseSignedClaims(ticket).getPayload();
            String source = claims.get("source", String.class);
            return new Grant(claims.get("training", Long.class), source == null ? Source.LEGACY : Source.valueOf(source));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("video event=playback_ticket_rejected reason={} video={} playbackRef={} traceId={}",
                e instanceof ExpiredJwtException ? "expired" : "invalid", video, VideoLog.id(ticket), TraceContext.get());
            throw new BusinessException(ErrorCode.FORBIDDEN, "视频播放凭证无效或已过期，请重新加载");
        }
    }
}
