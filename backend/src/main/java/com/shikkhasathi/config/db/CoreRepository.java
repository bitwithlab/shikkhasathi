package com.shikkhasathi.config.db;

import java.lang.annotation.*;

/** Spring Data repository bound to the CORE persistence unit. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CoreRepository {}
