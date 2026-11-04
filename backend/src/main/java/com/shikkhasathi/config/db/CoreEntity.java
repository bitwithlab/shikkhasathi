package com.shikkhasathi.config.db;

import java.lang.annotation.*;

/** Entity stored in the CORE database (people, money, private data — CLAUDE.md §0B). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CoreEntity {}
