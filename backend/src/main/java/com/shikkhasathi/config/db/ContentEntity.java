package com.shikkhasathi.config.db;

import java.lang.annotation.*;

/** Entity stored in the CONTENT database (catalog & learning material — CLAUDE.md §0B). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ContentEntity {}
