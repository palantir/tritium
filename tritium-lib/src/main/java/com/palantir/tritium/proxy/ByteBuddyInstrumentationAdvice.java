/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.proxy;

import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class must be public to be accessed by generated instrumentation, but is not considered API. */
public final class ByteBuddyInstrumentationAdvice {

    private static final Logger logger = LoggerFactory.getLogger(InvocationEventProxy.class);

    private ByteBuddyInstrumentationAdvice() {}

    /**
     * Work around slow <code>@Advice.Origin Method</code> parameters by providing an array of known methods
     * to each method, with the index of the current method bound individually.
     * https://github.com/raphw/byte-buddy/issues/714
     */
    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.PARAMETER)
    @interface MethodIndex {}

    @Nullable
    @Advice.OnMethodEnter(inline = false)
    public static InvocationContext enter(
            @Advice.This Object proxy,
            @Advice.AllArguments Object[] arguments,
            @Advice.FieldValue("instrumentationFilter") InstrumentationFilter filter,
            @Advice.FieldValue("invocationEventHandler") InvocationEventHandler<?> eventHandler,
            @Advice.FieldValue("methods") Method[] methods,
            @MethodIndex int index) {
        Method method = methods[index];
        if (isEnabled(proxy, method, arguments, filter, eventHandler)) {
            return handlePreInvocation(proxy, method, arguments, eventHandler);
        }
        return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, inline = false, backupArguments = false)
    public static void exit(
            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object result,
            @Advice.Thrown Throwable thrown,
            @Advice.FieldValue("invocationEventHandler") InvocationEventHandler<?> eventHandler,
            @Advice.Enter InvocationContext context) {
        if (context != null) {
            if (thrown == null) {
                handleOnSuccess(context, result, eventHandler);
            } else {
                handleOnFailure(context, thrown, eventHandler);
            }
        }
    }

    /**
     * Returns true if instrumentation handling is enabled, otherwise false.
     *
     * @return whether instrumentation handling is enabled
     */
    private static boolean isEnabled(
            Object instance,
            Method method,
            Object[] args,
            InstrumentationFilter filter,
            InvocationEventHandler<?> eventHandler) {
        try {
            return eventHandler.isEnabled()
                    && filter.shouldInstrument(instance, method, args);
        } catch (Throwable t) {
            logInvocationWarning("isEnabled", instance, method, t);
            return false;
        }
    }

    @Nullable
    private static InvocationContext handlePreInvocation(
            Object instance, Method method, Object[] args, InvocationEventHandler<?> eventHandler) {
        try {
            return eventHandler.preInvocation(instance, method, args);
        } catch (RuntimeException e) {
            logInvocationWarning("preInvocation", instance, method, e);
        }
        return null;
    }

    private static void handleOnSuccess(
            @Nullable InvocationContext context, @Nullable Object result, InvocationEventHandler<?> eventHandler) {
        try {
            eventHandler.onSuccess(context, result);
        } catch (RuntimeException e) {
            logInvocationWarningOnSuccess(context, result, e);
        }
    }

    private static void handleOnFailure(
            @Nullable InvocationContext context, Throwable cause, InvocationEventHandler<?> eventHandler) {
        try {
            eventHandler.onFailure(context, cause);
        } catch (RuntimeException e) {
            logInvocationWarningOnFailure(context, cause, e);
        }
    }

    private static void logInvocationWarningOnSuccess(
            @Nullable InvocationContext context,
            @Nullable Object result,
            Exception cause) {
        logInvocationWarning("onSuccess", context, result, cause);
    }

    private static void logInvocationWarningOnFailure(
            @Nullable InvocationContext context,
            @Nullable Throwable result,
            Exception cause) {
        logInvocationWarning("onFailure", context, result, cause);
    }

    private static SafeArg<String> safeSimpleClassName(@CompileTimeConstant String name, @Nullable Object object) {
        return SafeArg.of(name, (object == null) ? "null" : object.getClass().getSimpleName());
    }

    private static void logInvocationWarning(
            String event,
            @Nullable InvocationContext context,
            @Nullable Object result,
            Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("{} occurred handling '{}' ({}, {}): {}",
                    safeSimpleClassName("cause", cause),
                    SafeArg.of("event", event),
                    UnsafeArg.of("context", context),
                    safeSimpleClassName("result", result),
                    cause);
        }
    }

    private static void logInvocationWarning(
            String event,
            Object instance,
            Method method,
            Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("{} occurred handling '{}' invocation of {} {} on {} instance: {}",
                    safeSimpleClassName("cause", cause),
                    SafeArg.of("event", event),
                    SafeArg.of("class", method.getDeclaringClass().getName()),
                    SafeArg.of("method", method),
                    safeSimpleClassName("instanceClass", instance),
                    UnsafeArg.of("instance", instance),
                    cause);
        }
    }
}
