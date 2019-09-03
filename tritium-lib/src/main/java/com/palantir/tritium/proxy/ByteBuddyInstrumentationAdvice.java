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

final class ByteBuddyInstrumentationAdvice {

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
    @Advice.OnMethodEnter
    static InvocationContext enter(
            @Advice.This Object proxy,
            @Advice.AllArguments Object[] arguments,
            @Advice.FieldValue("instrumentationFilter") InstrumentationFilter filter,
            @Advice.FieldValue("invocationEventHandler") InvocationEventHandler<?> eventHandler,
            @Advice.FieldValue("methods") Method[] methods,
            @MethodIndex int index) {
        Method method = methods[index];
        try {
            if (eventHandler.isEnabled() && filter.shouldInstrument(proxy, method, arguments)) {
                return eventHandler.preInvocation(proxy, method, arguments);
            }
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "{} occurred handling 'preInvocation' invocation of {} {} on {} instance: {}",
                        safeSimpleClassName("cause", t),
                        SafeArg.of("class", method.getDeclaringClass().getName()),
                        SafeArg.of("method", method),
                        safeSimpleClassName("instanceClass", proxy),
                        UnsafeArg.of("instance", proxy),
                        t);
            }
        }
        return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, backupArguments = false)
    static void exit(
            @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object result,
            @Advice.Thrown Throwable thrown,
            @Advice.FieldValue("invocationEventHandler") InvocationEventHandler<?> eventHandler,
            @Advice.Enter InvocationContext context) {
        if (context != null) {
            try {
                if (thrown == null) {
                    eventHandler.onSuccess(context, result);
                } else {
                    eventHandler.onFailure(context, thrown);
                }
            } catch (RuntimeException e) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "{} occurred handling '{}' ({}, {}): {}",
                            safeSimpleClassName("cause", e),
                            SafeArg.of("event", thrown == null ? "onSuccess" : "onError"),
                            UnsafeArg.of("context", context),
                            safeSimpleClassName("result", thrown == null ? result : thrown),
                            e);
                }
            }
        }
    }

    private static SafeArg<String> safeSimpleClassName(@CompileTimeConstant String name, @Nullable Object object) {
        return SafeArg.of(name, (object == null) ? "null" : object.getClass().getSimpleName());
    }
}
