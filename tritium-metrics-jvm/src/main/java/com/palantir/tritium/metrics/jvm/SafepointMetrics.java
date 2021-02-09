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

package com.palantir.tritium.metrics.jvm;

import com.codahale.metrics.Gauge;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender.Size;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.FieldAccess;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Report duration spent waiting at safepoints. This could indicate badness in terms of STW GCing, or biased locking
 * going badly. See https://stackoverflow.com/questions/29666057/analyzing-gc-logs/29673564#29673564 for details. This
 * essentially provides the information of '+PrintGCApplicationStoppedTime' programmatically.
 */
final class SafepointMetrics {
    private static final String RUNTIME_FIELD = "runtime";
    private static final Logger log = LoggerFactory.getLogger(SafepointMetrics.class);
    private static final Optional<Gauge<Long>> gauge = getGauge();

    static void register(TaggedMetricRegistry registry) {
        gauge.ifPresent(g -> InternalJvmMetrics.of(registry).safepointTime(g));
    }

    /**
     * This is somewhat involved. Basically, Java 11+ does not let you compile against sun.management classes when using
     * the --release flag. But the classes are present at runtime. We used to use reflection to access this, but the
     * reflection is caught by JDK internal security and eventually will be blocked by the module system.
     * So, we generate a short class where we call the actual method, which does not have the same module boundary
     * issues.
     *
     * Code should be equivalent to:
     *
     * <pre>
     *     class SomeGauge implements Gauge {
     *         private static final HotspotRuntimeMBean runtime = ManagementFactoryHelper.getHotspotRuntimeMBean();
     *
     *         public Object getValue() {
     *             return runtime.getTotalSafepointTime();
     *         }
     *     }
     * </pre>
     *
     */
    @SuppressWarnings("unchecked")
    private static Optional<Gauge<Long>> getGauge() {
        try {
            Class<?> managementFactory = Class.forName("sun.management.ManagementFactoryHelper");
            Class<?> runtimeMbean = Class.forName("sun.management.HotspotRuntimeMBean");
            Gauge<Long> gaugeImplementation = (Gauge<Long>) new ByteBuddy()
                    .subclass(Object.class)
                    .implement(Gauge.class)
                    .defineField(
                            RUNTIME_FIELD, runtimeMbean, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                    .initializer((methodVisitor, implementationContext, instrumentedMethod) -> {
                        StackManipulation.Size size = new StackManipulation.Compound(
                                        // call method, put result on top of stack
                                        MethodInvocation.invoke(new TypeDescription.ForLoadedType(managementFactory)
                                                .getDeclaredMethods()
                                                .filter(ElementMatchers.named("getHotspotRuntimeMBean"))
                                                .getOnly()),
                                        // write element on top of stack into appropriate field
                                        FieldAccess.forField(implementationContext
                                                        .getInstrumentedType()
                                                        .getDeclaredFields()
                                                        .filter(ElementMatchers.named(RUNTIME_FIELD))
                                                        .getOnly())
                                                .write())
                                .apply(methodVisitor, implementationContext);
                        return new Size(size.getMaximalSize(), instrumentedMethod.getStackSize());
                    })
                    .method(ElementMatchers.named("getValue"))
                    .intercept(MethodCall.invoke(ElementMatchers.named("getTotalSafepointTime"))
                            .onField("runtime"))
                    .make()
                    .load(SafepointMetrics.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                    .getLoaded()
                    .getConstructor()
                    .newInstance();
            gaugeImplementation.getValue();
            return Optional.of(gaugeImplementation);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException e) {
            log.info("Could not get the total safepoint time, these metrics will not be registered.", e);
            return Optional.empty();
        }
    }

    private SafepointMetrics() {
        throw new UnsupportedOperationException();
    }
}
