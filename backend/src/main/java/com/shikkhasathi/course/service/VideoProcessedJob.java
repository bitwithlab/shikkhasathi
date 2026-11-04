package com.shikkhasathi.course.service;

import com.shikkhasathi.common.jobs.EnqueueOptions;
import com.shikkhasathi.common.jobs.JobHandler;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.course.adapter.VideoService;
import com.shikkhasathi.course.repository.CourseAuthoringRepository;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** {@code video.processed}: poll the provider after upload until the asset is ready (webhooks also land here). */
@Component
public class VideoProcessedJob implements JobHandler<VideoProcessedJob.Payload> {
  public record Payload(UUID videoAssetId, Integer attempt) {}

  private final CourseAuthoringRepository repo;
  private final VideoService video;
  private final JobQueue jobs;

  public VideoProcessedJob(CourseAuthoringRepository repo, VideoService video, @Lazy JobQueue jobs) {
    this.repo = repo;
    this.video = video;
    this.jobs = jobs;
  }

  @Override
  public String name() {
    return JobNames.VIDEO_PROCESSED;
  }

  @Override
  public Class<Payload> payloadType() {
    return Payload.class;
  }

  @Override
  public void handle(Payload p) {
    int attempt = p.attempt() == null ? 0 : p.attempt();
    var v = repo.video(p.videoAssetId()).orElse(null);
    if (v == null || v.providerVideoId() == null || v.providerVideoId().isEmpty() || "ready".equals(v.status())) return;
    var st = video.provider().status(v.providerVideoId());
    repo.updateVideo(v.id(), st.status(), st.durationSec() != 0 ? st.durationSec() : v.durationSec());
    if ("processing".equals(st.status()) && attempt < 40) jobs.enqueue(JobNames.VIDEO_PROCESSED, new Payload(v.id(), attempt + 1), EnqueueOptions.delay(60_000));
  }
}
