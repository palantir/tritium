/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class AnnotationHelper {

    private AnnotationHelper() { throw new UnsupportedOperationException(); }

    /**
     * Annotation as implemented on passed in type or parent of that type, works for both super classes and
     * interfaces
     *
     * @param annotation
     * @param <T>
     * @return First matching annotation found in depth first search, or null if not found
     */
    public static <T extends Annotation> T getSuperTypeAnnotation(Class<?> c, Class<T> annotation) {
        if(c.isAnnotationPresent(annotation)) {
            return c.getAnnotation(annotation);
        }

        for(Class<?> ifaces : getParentClasses(c)) {
            T superAnnotation = getSuperTypeAnnotation(ifaces, annotation);
            if(superAnnotation != null) {
                return superAnnotation;
            }
        }

        return null;
    }

    /**
     * Shortcut for getMethodAnnotation(annotation, c, method.getName(), method.getParameterTypes())
     **/
    public static <T extends Annotation> T getMethodAnnotation(Class<T> annotation,
            Class<?> c, Method method) {
        return getMethodAnnotation(annotation, c, method.getName(), method.getParameterTypes());
    }

    /**
     * Depth first search up the Type hierarchy to find a matching annotation,  Types which do not implement the
     * specified method signature are ignored
     *
     * @param annotation
     * @param c
     * @param methodName
     * @param parameterTypes
     * @param <T>
     * @return - First found matching annotation or null
     */
    public static <T extends Annotation> T getMethodAnnotation(Class<T> annotation,
            Class<?> c, String methodName, Class... parameterTypes) {

        Method method;
        try {
            method = c.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }

        if (method.isAnnotationPresent(annotation)) {
            return method.getAnnotation(annotation);
        }

        for(Class<?> iface : getParentClasses(c)){
            T foundAnnotation = getMethodAnnotation(annotation, iface, methodName, parameterTypes);
            if(foundAnnotation != null) {
                return foundAnnotation;
            }
        }

        return null;
    }

    private static List<Class<?>> getParentClasses(Class<?> clazz) {
        ImmutableList.Builder<Class<?>> builder = new ImmutableList.Builder<>();
        if(clazz.getSuperclass() != null) {
            builder.add(clazz.getSuperclass());
        }

        return builder.addAll(Arrays.asList(clazz.getInterfaces())).build();
    }
}
