package com.aiquestion.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dedicated single worker: rendering never blocks the application's status-update scheduler. */
@Component
@ConditionalOnProperty(name="training.preview.enabled",havingValue="true",matchIfMissing=true)
public class MaterialPreviewWorker {
  private final MaterialPreviewService previews;
  private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread=new Thread(r,"material-preview"); thread.setDaemon(true); return thread;
  });
  public MaterialPreviewWorker(MaterialPreviewService previews) { this.previews=previews; }
  @PostConstruct public void start() {
    worker.scheduleWithFixedDelay(() -> {
      try { previews.processPending(); }
      catch (Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).error("Preview queue processing failed",e); }
    },5,5,TimeUnit.SECONDS);
  }
  @PreDestroy public void stop() { worker.shutdownNow(); }
}
