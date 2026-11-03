package com.shikkhasathi.shared.navigation.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.ClipboardCheck
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquarePlus
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.Presentation
import com.composables.icons.lucide.School
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.UserRound
import com.shikkhasathi.shared.navigation.DrawerDestination
import com.shikkhasathi.shared.navigation.TopDestination

/** Lucide icons only (CLAUDE.md §7: no emoji, no Material default icon set). */
val TopDestination.icon: ImageVector
    get() =
        when (this) {
            TopDestination.Home -> Lucide.House
            TopDestination.Classroom -> Lucide.School
            TopDestination.Courses -> Lucide.BookOpen
            TopDestination.Exam -> Lucide.Timer
            TopDestination.Profile -> Lucide.UserRound
        }

val DrawerDestination.icon: ImageVector
    get() =
        when (this) {
            DrawerDestination.Explore -> Lucide.Search
            DrawerDestination.MockTests -> Lucide.ClipboardCheck
            DrawerDestination.Notes -> Lucide.NotebookPen
            DrawerDestination.Requests -> Lucide.MessageSquarePlus
            DrawerDestination.Notifications -> Lucide.Bell
            DrawerDestination.Settings -> Lucide.Settings
            DrawerDestination.TeacherStudio -> Lucide.Presentation
        }
