package com.shikkhasathi.course.event;

import java.util.UUID;

/**
 * Published (Spring {@code ApplicationEventPublisher}) by course review when a course becomes published.
 * Listeners: course requests (notify voters, mark fulfilled) — previously {@code onCoursePublished}.
 * Listeners run synchronously in the publisher's thread, after the status change is committed.
 */
public record CoursePublishedEvent(UUID courseId) {}
