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
    }

    @Test
    fun notificationLinksOfPhase11OpenTheirScreens() {
        // Shapes the server sends (Web/server notification `link`s).
        assertEquals(PlayerRoute("c-1", "l-9", tab = "qa"), routeForLink("/learn/c-1/l-9?tab=qa"))
        assertEquals(PlayerRoute("c-1"), routeForLink("/learn/c-1"))
        assertEquals(ExamPlanRoute("p-7"), routeForLink("/exam-mode/p-7"))
        assertEquals(ExamModeRoute, routeForLink("/exam-mode"))
        assertEquals(MyCoursesRoute, routeForLink("/my-courses"))
        assertEquals(ProfileRoute, routeForLink("/profile#orders"))
        assertEquals(CourseDetailRoute("ds-midterm-cse-2203"), routeForLink("/c/ds-midterm-cse-2203"))
        assertEquals(MockAttemptRoute("t-1"), routeForLink("/mock-test/t-1"))
        assertEquals(MockResultRoute("a-1"), routeForLink("/mock-test/a-1/result"))
        assertEquals(MockTestsRoute, routeForLink("/mock-tests"))
        assertEquals(CheckoutRoute("c-1"), routeForLink("/checkout/c-1"))
        assertEquals(CheckoutResultRoute("o-1"), routeForLink("/checkout/result?order=o-1&status=paid"))
        assertEquals(CheckoutResultRoute(null), routeForLink("/checkout/result?status=failed"))
        assertNull(routeForLink("/teach/earnings"), "teacher pages stay on the web")
    }

    @Test
    fun appLinksAreAcceptedOnlyForOurHosts() {
        assertEquals(PlayerRoute("c-1", "l-2"), routeForLink("https://shikkhasathi.com/learn/c-1/l-2"))
        assertEquals(CheckoutResultRoute("o-1"), routeForLink("https://www.shikkhasathi.com/checkout/result?order=o-1"))
        assertEquals(HomeRoute, routeForLink("https://shikkhasathi.com"))
        assertEquals(ExploreRoute, routeForLink("https://shikkhasathi.com/explore?q=data+structures"))
        assertNull(routeForLink("https://shikkhasathi.com.evil.test/learn/c-1"))
        assertNull(routeForLink("https://classroom.google.com/c/123"))
        assertNull(routeForLink("javascript:alert(1)"))
    }

    @Test
    fun drawerListsClassroomShortcutsBeforeNotifications() {
        val labels = DrawerDestination.visibleFor(isSeller = false)
        assertEquals(DrawerDestination.Assignments, labels[labels.indexOf(DrawerDestination.Notifications) - 2])
        assertEquals(false, DrawerDestination.TeacherStudio in labels)
        assertEquals(DrawerDestination.Downloads, labels[labels.indexOf(DrawerDestination.Notifications) + 1])
    }
}
