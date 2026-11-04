package com.shikkhasathi.learning.service;

import com.shikkhasathi.student.service.DashboardWidgetProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Home dashboard "পড়া চালিয়ে যান" widget: the most recently accessed in-progress course (or null). */
@Component
public class ContinueLearningProvider implements DashboardWidgetProvider {
  private final PlayerService player;

  public ContinueLearningProvider(PlayerService player) {
    this.player = player;
  }

  @Override
  public String widget() {
    return CONTINUE_LEARNING;
  }

  @Override
  public Object load(UUID userId) {
    return player.continueLearning(userId);
  }
}
