/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.tracing;

import static com.palantir.logsafe.Preconditions.checkArgument;
import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.google.common.base.Throwables;
import com.palantir.logsafe.SafeArg;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class ReflectiveTracer implements Tracer {

    private final Method startSpanMethod;
    private final Method completeSpanMethod;

    ReflectiveTracer(Method startSpanMethod, Method completeSpanMethod) {
        this.startSpanMethod = checkStartMethod(startSpanMethod);
        this.completeSpanMethod = checkCompleteMethod(completeSpanMethod);
    }

    @Override
    public void startSpan(String operationName) {
        try {
            startSpanMethod.invoke(null, operationName);
        } catch (ReflectiveOperationException e) {
            throw throwUnchecked(e);
        }
    }

    @Override
    public void completeSpan() {
        try {
            completeSpanMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw throwUnchecked(e);
        }
    }

    private static Method checkStartMethod(Method method) {
        checkNotNull(method, "method");
        checkPublicStaticMethod(method);
        checkArgument(
                method.getParameterCount() == 1 && String.class.equals(method.getParameterTypes()[0]),
                "startSpan method should take 1 String argument",
                SafeArg.of("method", method),
                SafeArg.of("argumentTypes", paramsToClassNames(method)));
        return method;
    }

    private static Method checkCompleteMethod(Method method) {
        checkNotNull(method, "method");
        checkPublicStaticMethod(method);
        checkArgument(method.getParameterCount() == 0,
                "completeSpan method should take 0 arguments",
                SafeArg.of("method", method),
                SafeArg.of("argumentTypes", paramsToClassNames(method)));
        return method;
    }

    private static void checkPublicStaticMethod(Method method) {
        checkArgument(Modifier.isPublic(method.getModifiers()), "method must be public", SafeArg.of("method", method));
        checkArgument(Modifier.isStatic(method.getModifiers()), "method must be static", SafeArg.of("method", method));
    }

    private static List<String> paramsToClassNames(Method method) {
        return Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.toList());
    }

    private static RuntimeException throwUnchecked(ReflectiveOperationException reflectionException) {
        Throwable cause = reflectionException.getCause() != null ? reflectionException.getCause() : reflectionException;
        Throwables.throwIfUnchecked(cause);
        throw new IllegalStateException(cause);
    }

}
