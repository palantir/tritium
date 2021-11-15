/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.tritium.event.metrics.annotations;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.annotation.Nullable;

public final class AnnotationHelper {

    private AnnotationHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Annotation as implemented on passed in type or parent of that type, works for both super classes and interfaces.
     *
     * @param clazz - Class type to scan for annotations
     * @param annotation - Annotation type to scan for
     * @return - First matching annotation found in depth first search, or null if not found
     */
    @Nullable
    public static <T extends Annotation> T getSuperTypeAnnotation(Class<?> clazz, Class<T> annotation) {
        if (clazz.isAnnotationPresent(annotation)) {
            return clazz.getAnnotation(annotation);
        }

        for (Class<?> ifaces : getParentClasses(clazz)) {
            T superAnnotation = getSuperTypeAnnotation(ifaces, annotation);
            if (superAnnotation != null) {
                return superAnnotation;
            }
        }

        return null;
    }

    /**
     * Depth first search up the Type hierarchy to find a matching annotation, Types which do not implement the
     * specified method signature are ignored.
     *
     * @param annotation - Annotation type to scan for
     * @param clazz - Class type to scan for matching annotations
     * @param methodSignature - Method to search annotation for
     * @return - First found matching annotation or null
     */
    @Nullable
    public static <T extends Annotation> T getMethodAnnotation(
            Class<T> annotation, Class<?> clazz, MethodSignature methodSignature) {
        Method method;
        try {
            method = clazz.getMethod(methodSignature.getMethodName(), methodSignature.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }

        if (method.isAnnotationPresent(annotation)) {
            return method.getAnnotation(annotation);
        }

        for (Class<?> iface : getParentClasses(clazz)) {
            T foundAnnotation = getMethodAnnotation(annotation, iface, methodSignature);
            if (foundAnnotation != null) {
                return foundAnnotation;
            }
        }

        return null;
    }

    @VisibleForTesting
    private static ImmutableSet<Class<?>> getParentClasses(Class<?> clazz) {
        ImmutableSet.Builder<Class<?>> builder = ImmutableSet.builder();
        builder.add(clazz.getInterfaces());
        Class<?> superclass = clazz.getSuperclass();
        while (superclass != null) {
            builder.add(superclass.getInterfaces());
            builder.add(superclass);
            superclass = superclass.getSuperclass();
        }
        return builder.build();
    }

    public static final class MethodSignature {
        private final String methodName;
        private final Class<?>[] parameterTypes;

        private static final Class<?>[] NO_ARGS = new Class<?>[0];

        private MethodSignature(String methodName, @Nullable Class<?>... parameterTypes) {
            this.methodName = checkNotNull(methodName);
            this.parameterTypes =
                    (parameterTypes == null || parameterTypes.length == 0) ? NO_ARGS : parameterTypes.clone();
        }

        public String getMethodName() {
            return methodName;
        }

        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        @Override
        public String toString() {
            return "MethodSignature{"
                    + "methodName='"
                    + methodName
                    + '\''
                    + ", parameterTypes="
                    + Arrays.toString(parameterTypes)
                    + '}';
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }

            MethodSignature that = (MethodSignature) other;

            if (getMethodName() != null
                    ? !getMethodName().equals(that.getMethodName())
                    : that.getMethodName() != null) {
                return false;
            }
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            return Arrays.equals(getParameterTypes(), that.getParameterTypes());
        }

        @Override
        public int hashCode() {
            int result = getMethodName() != null ? getMethodName().hashCode() : 0;
            result = 31 * result + Arrays.hashCode(getParameterTypes());
            return result;
        }

        public static MethodSignature of(Method method) {
            return MethodSignature.of(method.getName(), method.getParameterTypes());
        }

        public static MethodSignature of(String methodName, Class<?>... parameterTypes) {
            return new MethodSignature(methodName, parameterTypes);
        }
    }
}
