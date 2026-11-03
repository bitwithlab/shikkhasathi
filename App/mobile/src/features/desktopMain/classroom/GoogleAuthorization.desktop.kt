package com.shikkhasathi.features.classroom.data

/**
 * Desktop is a dev/test harness: it has no Google consent UI, so it hands the API a `fake…` code. Outside
 * production the server answers with its FakeClassroomClient (realistic CSE classes), so the whole connect →
 * sync → dashboard flow can be exercised without a Google account.
 */
class DesktopGoogleAuthorization : GoogleAuthorization by FakeGoogleAuthorization("fake-desktop-dev")
