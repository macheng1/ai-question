package com.aiquestion.config;

import com.aiquestion.common.VideoLog;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Logs the final status after MVC writes the resource, including partial and unsatisfiable ranges. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);
  private static final Pattern UPLOAD_ID = Pattern.compile("(/management/video-uploads/)([^/]+)");
  private static final Pattern MATRIX_PARAMETERS = Pattern.compile(";[^/]*");
  private static final Pattern VIDEO_CONTENT = Pattern.compile("/video-playback/([0-9]+)$");
  private static final Pattern BYTE_RANGE = Pattern.compile("(?i:bytes)(?:=| )[0-9,*/ -]+");
  private final String traceHeader;

  public RequestLogFilter(@Value("${app.trace-header:X-Trace-Id}") String traceHeader) {
    this.traceHeader = traceHeader;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    Exception failure = null;
    try {
      filterChain.doFilter(request, response);
    } catch (Exception ex) {
      failure = ex;
      throw ex;
    } finally {
      String traceId = response.getHeader(traceHeader);
      String uri = MATRIX_PARAMETERS.matcher(request.getRequestURI()).replaceAll("");
      var video = VIDEO_CONTENT.matcher(uri);
      String error = failure == null ? "-" : failure.getClass().getSimpleName();
      if (video.find()) {
        var source = request.getAttribute("videoPlaybackSource");
        log.info("video event=playback_http method={} video={} source={} verified={} playbackRef={} range={} status={} contentRange={} contentLength={} durationMs={} error={} traceId={}",
            request.getMethod(), video.group(1), source == null ? "-" : source, source != null,
            VideoLog.id(request.getParameter("ticket")), range(request.getHeader("Range")), response.getStatus(),
            range(response.getHeader("Content-Range")), response.getHeader("Content-Length"), VideoLog.elapsedMs(start), error, traceId);
      } else if (uri.contains("/video-uploads") || uri.contains("/videos/") || uri.endsWith("/videos")) {
        String safePath = UPLOAD_ID.matcher(uri).replaceFirst(match -> match.group(1) + VideoLog.id(match.group(2)));
        log.info("video event=request_http method={} uri={} status={} durationMs={} error={} traceId={}",
            request.getMethod(), safePath, response.getStatus(), VideoLog.elapsedMs(start), error, traceId);
      } else {
        log.info("request method={} uri={} status={} durationMs={} traceId={} ip={}", request.getMethod(), uri,
            response.getStatus(), VideoLog.elapsedMs(start), traceId, request.getRemoteAddr());
        if (failure != null) log.error("request failed traceId={}", traceId, failure);
      }
    }
  }

  private static String range(String header) {
    if (header == null) return "-";
    return header.length() <= 256 && BYTE_RANGE.matcher(header).matches() ? header : "invalid-or-too-long";
  }
}
