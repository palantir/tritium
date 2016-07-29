/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.commons.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

/**
 * Sets the style to be used by the Immutables library.
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Value.Style(
        get = {"get*", "is*"}, // detect 'get' and 'is' prefixes in accessor methods
        init = "set*", // builder initialization methods will have 'set' prefix
        typeAbstract = {"Abstract*"}, // 'Abstract' prefix will be detected and trimmed
        typeImmutable = "Immutable_*", // prefix for generated immutable type
        visibility = ImplementationVisibility.PUBLIC) // generated class will be always be public
public @interface ImmutablesStyle {
    // marker-only
}
