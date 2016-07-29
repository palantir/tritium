/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.commons.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * This annotation can be applied to a package to indicate that the fields, methods, and parameters in all the packages'
 * classes are {@link Nonnull} by default. That is, unless:
 * <ul>
 * <li>The method overrides a method in a superclass (in which case the annotation of the corresponding parameter in the
 * superclass applies).
 * <li>There is an explicit nullness annotation ({@link Nonnull}.
 * <li>There is a default annotation applied to a more tightly nested element.
 * </ul>
 *
 * @author bworth
 */
@Documented
@Nonnull
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
@TypeQualifierDefault({
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER
})
public @interface NonnullByDefault {
    // marker-only
}
