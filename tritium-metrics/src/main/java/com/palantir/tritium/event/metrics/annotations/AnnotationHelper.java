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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * Do not use, will be removed in future release.
 * @deprecated Do not use, will be removed in future release.
 */
@Deprecated
@SuppressWarnings("InlineMeSuggester") // this will just go away
public final class AnnotationHelper {

    private AnnotationHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Do not use, will be removed in future release.
     * @return always null
     * @deprecated Do not use, will be removed in future release.
     */
    @Deprecated
    @Nullable
    public static <T extends Annotation> T getSuperTypeAnnotation(Class<?> _clazz, Class<T> _annotation) {
        return null;
    }

    /**
     * Do not use, will be removed in future release.
     * @return always null
     * @deprecated Do not use, will be removed in future release.
     */
    @Deprecated
    @Nullable
    public static <T extends Annotation> T getMethodAnnotation(
            Class<T> _annotation, Class<?> _clazz, MethodSignature _methodSignature) {
        return null;
    }

    @Deprecated
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
