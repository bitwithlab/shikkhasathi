package com.shikkhasathi.core.testing

import com.shikkhasathi.core.model.AcademicSummary
import com.shikkhasathi.core.model.AuthTokens
import com.shikkhasathi.core.model.Me
import com.shikkhasathi.core.model.Role

fun sampleMe(name: String = "রাফি আহমেদ") =
    Me(
        id = "0190f0a3-5f5e-7c1a-9d1e-1a2b3c4d5e6f",
        name = name,
        phone = "+8801712345678",
        roles = listOf(Role.Student),
        academic =
            AcademicSummary(
                institutionName = "Metropolitan University",
                institutionShort = "MU",
                departmentShort = "CSE",
                semester = 5,
                batch = "56",
                section = "A",
            ),
        createdAt = "2026-11-03T10:00:00.000Z",
    )

fun sampleTokens(
    access: String = "access-1",
    refresh: String? = "refresh-1",
    user: Me = sampleMe(),
) = AuthTokens(
    accessToken = access,
    accessTokenExpiresAt = "2026-11-03T10:15:00.000Z",
    refreshToken = refresh,
    user = user,
    isNewUser = false,
)
