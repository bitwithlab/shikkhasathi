package com.shikkhasathi.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutesTest {
    @Test
    fun webLinksFromTheApiMapToScreens() {
        assertEquals(AssignmentsRoute(), routeForLink("/classroom/assignments?id=cw-102"))
        assertEquals(ClassStreamRoute("gc 2203"), routeForLink("/classroom/gc%202203?tab=materials"))
        assertEquals(CalendarRoute, routeForLink("/classroom/calendar"))
        assertEquals(NotificationsRoute, routeForLink("/notifications/"))
        assertEquals(HomeRoute, routeForLink("/"))
        assertNull(routeForLink("/c/ds-midterm-cse-2203"), "no course page in the app yet → web")
    }

    @Test
    fun drawerListsClassroomShortcutsBeforeNotifications() {
        val labels = DrawerDestination.visibleFor(isSeller = false)
        assertEquals(DrawerDestination.Assignments, labels[labels.indexOf(DrawerDestination.Notifications) - 2])
        assertEquals(false, DrawerDestination.TeacherStudio in labels)
    }
}
