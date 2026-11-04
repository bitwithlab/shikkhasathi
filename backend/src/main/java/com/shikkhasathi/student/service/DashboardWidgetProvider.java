package com.shikkhasathi.student.service;

import java.util.UUID;

/**
 * Extension point for dashboard widgets owned by other modules (previously {@code setDashboardProvider}).
 * Implement as a Spring bean; the dashboard picks providers up by {@link #widget()}:
 *
 * <ul>
 *   <li>{@code "continueLearning"} (learning module) → the {@code continueLearning.data} object, or {@code null}
 *   <li>{@code "recommendations"} (discovery module) → the {@code recommendations.data} list (CourseCard[])
 * </ul>
 *
 * The returned object is serialized as-is into the dashboard response, so it must match the contract shape.
 */
public interface DashboardWidgetProvider {
  String CONTINUE_LEARNING = "continueLearning";
  String RECOMMENDATIONS = "recommendations";

  String widget();

  Object load(UUID userId);
}
