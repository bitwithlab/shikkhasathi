package com.shikkhasathi.core.network

import kotlinx.serialization.json.Json

/**
 * JSON settings shared by the API client and secure storage.
 * - unknown keys are ignored (the server may add fields before the app updates);
 * - nulls are not written, so Zod `.optional()` fields are omitted instead of sent as `null`;
 * - defaults are written (e.g. `platform`), because the server's defaults are web-oriented.
 */
val SsJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
        isLenient = false
    }
