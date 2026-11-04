package com.shikkhasathi.marketplace.service;

import com.shikkhasathi.student.service.DashboardWidgetProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Dashboard "আপনার জন্য কোর্স" widget: the top 6 recommendations (CourseCard[]). */
@Component
public class RecommendationsWidget implements DashboardWidgetProvider {
  private final CourseService courses;

  public RecommendationsWidget(CourseService courses) {
    this.courses = courses;
  }

  @Override
  public String widget() {
    return RECOMMENDATIONS;
  }

  @Override
  public Object load(UUID userId) {
    return courses.recommendations(userId, 6).items();
  }
}
