package com.shikkhasathi.features.testing

import kotlin.time.Clock
import kotlin.time.Instant

/** "Now" for every fixture: Tuesday 3 Nov 2026, 10:00 in Dhaka (04:00 UTC). */
val FIXTURE_NOW: Instant = Instant.parse("2026-11-03T04:00:00Z")

class FixedClock(var now: Instant = FIXTURE_NOW) : Clock {
    override fun now(): Instant = now
}

/**
 * A realistic `GET /dashboard` answer (contracts/classroom.ts `Dashboard`), as the server sends it:
 * connected Classroom, 7 due items over four groups, an unread feed, an exam plan, no purchased course yet,
 * a failed recommendations widget, and an unknown extra field (must be ignored).
 */
val DASHBOARD_JSON: String =
    """
    {
      "header": {
        "name": "রাফি আহমেদ",
        "institutionShort": "MU",
        "departmentShort": "CSE",
        "semester": 5,
        "unreadNotifications": 3,
        "classroom": {
          "connected": true,
          "status": "active",
          "email": "rafi.cse56@student.metrouni.edu.bd",
          "lastSyncAt": "2026-11-03T03:58:00.000Z",
          "lastFullSyncAt": "2026-11-01T12:00:00.000Z",
          "nextManualSyncAt": null,
          "errorCode": null,
          "courseCount": 4,
          "isFake": false
        }
      },
      "dueSoon": {
        "data": {
          "groups": [
            { "key": "overdue", "items": [
              ${assignment("cw-101", "CSE-2203 Lab 4: Linked List Implementation", "2026-11-02T17:59:00.000Z", "missing")}
            ] },
            { "key": "today", "items": [
              ${assignment("cw-102", "BST Deletion & AVL Tree Rotation", "2026-11-03T17:59:00.000Z", "pending")},
              ${assignment("cw-103", "Quiz 2 practice sheet", "2026-11-03T08:00:00.000Z", "turned_in")}
            ] },
            { "key": "tomorrow", "items": [
              ${assignment(
        "cw-104",
        "OOP II Assignment 3 (Java Generics)",
        "2026-11-04T12:00:00.000Z",
        "pending",
        course = "CSE-2201 OOP II"
    )}
            ] },
            { "key": "this_week", "items": [
              ${assignment(
        "cw-105",
        "Discrete Math Problem Set 5",
        "2026-11-06T17:59:00.000Z",
        "pending",
        course = "MAT-2105 Discrete Math"
    )},
              ${assignment(
        "cw-106",
        "Digital Logic Lab Report 3",
        "2026-11-07T17:59:00.000Z",
        "pending",
        course = "CSE-2207 Digital Logic"
    )},
              ${assignment(
        "cw-107",
        "Technical Writing: Abstract draft",
        "2026-11-08T17:59:00.000Z",
        "pending",
        course = "ENG-2101 Technical Writing"
    )}
            ] }
          ],
          "total": 7
        },
        "error": null
      },
      "feed": {
        "data": {
          "items": [
            {
              "id": "0190f0a3-0000-7000-8000-000000000001",
              "kind": "material",
              "refId": "mat-9",
              "courseId": "gc-2203",
              "courseName": "CSE-2203 Data Structures",
              "courseColor": "#4F46E5",
              "title": "Lecture 3.2 slides: Recursion tree method",
              "body": null,
              "materials": [ { "kind": "drive", "title": "L3.2-recursion-tree.pdf", "url": "https://drive.google.com/file/d/abc", "thumbnailUrl": null, "mime": "application/pdf" } ],
              "grade": null,
              "alternateLink": "https://classroom.google.com/c/MjIwMw/m/OQ",
              "occurredAt": "2026-11-03T03:30:00.000Z",
              "read": false
            },
            {
              "id": "0190f0a3-0000-7000-8000-000000000002",
              "kind": "graded",
              "refId": "cw-090",
              "courseId": "gc-2201",
              "courseName": "CSE-2201 OOP II",
              "courseColor": "#10B981",
              "title": "Lab 2: Inheritance",
              "body": null,
              "materials": [],
              "grade": { "assigned": 18, "max": 20 },
              "alternateLink": "https://classroom.google.com/c/MjIwMQ/a/OTA",
              "occurredAt": "2026-11-02T10:00:00.000Z",
              "read": true
            }
          ],
          "unread": 1
        },
        "error": null
      },
      "exam": {
        "data": {
          "planId": "0190f0a3-0000-7000-8000-0000000000e1",
          "subjectName": "Data Structures",
          "courseCode": "CSE-2203",
          "examType": "midterm",
          "examDate": "2026-11-15T03:00:00.000Z",
          "daysLeft": 12,
          "readiness": 42
        },
        "error": null
      },
      "continueLearning": { "data": null, "error": null },
      "week": {
        "data": [
          { "date": "2026-11-03", "deadlines": 2, "exams": 0 },
          { "date": "2026-11-04", "deadlines": 1, "exams": 0 },
          { "date": "2026-11-05", "deadlines": 0, "exams": 0 },
          { "date": "2026-11-06", "deadlines": 1, "exams": 0 },
          { "date": "2026-11-07", "deadlines": 1, "exams": 0 },
          { "date": "2026-11-08", "deadlines": 1, "exams": 0 },
          { "date": "2026-11-09", "deadlines": 0, "exams": 1 }
        ],
        "error": null
      },
      "recommendations": { "data": null, "error": "unavailable" },
      "generatedAt": "2026-11-03T04:00:00.000Z",
      "serverAddedLater": { "ignored": true }
    }
    """.trimIndent()

fun assignment(
    id: String,
    title: String,
    dueAt: String?,
    status: String,
    course: String = "CSE-2203 Data Structures",
    courseId: String = "gc-" + course.substringAfter('-').substringBefore(' '),
): String =
    """
    {
      "id": "$id",
      "courseId": "$courseId",
      "courseName": "$course",
      "courseColor": "#4F46E5",
      "title": "$title",
      "description": null,
      "workType": "ASSIGNMENT",
      "dueAt": ${dueAt?.let { "\"$it\"" } ?: "null"},
      "maxPoints": 20,
      "status": "$status",
      "late": false,
      "grade": null,
      "materials": [],
      "alternateLink": "https://classroom.google.com/c/MjIwMw/a/$id/details",
      "postedAt": "2026-10-28T06:00:00.000Z"
    }
    """.trimIndent()

/** Same answer with Classroom not connected (server sends null Classroom sections). */
val DASHBOARD_NOT_CONNECTED_JSON: String =
    """
    {
      "header": {
        "name": "নুসরাত জাহান",
        "institutionShort": null, "departmentShort": null, "semester": null,
        "unreadNotifications": 0,
        "classroom": { "connected": false, "status": null, "email": null, "lastSyncAt": null, "lastFullSyncAt": null,
                       "nextManualSyncAt": null, "errorCode": null, "courseCount": 0, "isFake": false }
      },
      "dueSoon": { "data": null, "error": null },
      "feed": { "data": null, "error": null },
      "exam": { "data": null, "error": null },
      "continueLearning": { "data": null, "error": null },
      "week": { "data": [ { "date": "2026-11-03", "deadlines": 0, "exams": 0 } ], "error": null },
      "recommendations": { "data": [], "error": null },
      "generatedAt": "2026-11-03T04:00:00.000Z"
    }
    """.trimIndent()
