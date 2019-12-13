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

import static com.palantir.logsafe.Preconditions.checkArgument;
import static com.palantir.logsafe.Preconditions.checkNotNull;
import static com.palantir.logsafe.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.CompositeInvocationEventHandler;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.TypeCache;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ByteBuddyInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(ByteBuddyInstrumentation.class);
    // Offset to avoid duplicate fqcns
    private static final AtomicInteger offset = new AtomicInteger();
    // Reuse generated classes when possible
    private static final TypeCache<ImmutableList<Class<?>>> cache =
            new TypeCache.WithInlineExpunction<>(TypeCache.Sort.WEAK);
    private static final Joiner UNDERSCORE_JOINER = Joiner.on('_');
    private static final String LOGGER_FIELD = "log";
    private static final String METHODS_FIELD = "methods";
    private static final String DISABLED_HANDLER_SENTINEL_FIELD = "DISABLED_HANDLER_SENTINEL";

    private ByteBuddyInstrumentation() {
        throw new UnsupportedOperationException();
    }

    static <T, U extends T> T instrument(
            Class<T> interfaceClass,
            U delegate,
            List<InvocationEventHandler<InvocationContext>> handlers,
            InstrumentationFilter instrumentationFilter) {
        checkNotNull(interfaceClass, "interfaceClass");
        checkNotNull(delegate, "delegate");
        checkNotNull(instrumentationFilter, "instrumentationFilter");
        checkNotNull(handlers, "handlers");

        if (!isAccessible(interfaceClass)) {
            log.warn("Interface {} is not accessible. Delegate {} of type {} will not be instrumented",
                    SafeArg.of("interface", interfaceClass),
                    UnsafeArg.of("delegate", delegate),
                    SafeArg.of("delegateType", delegate.getClass()));
            return delegate;
        }

        // Use the interface classloader to avoid creating additional proxies for other loaders delegating
        // to the same type.
        ClassLoader classLoader = getClassLoader(interfaceClass);
        if (!isClassLoadable(classLoader, InvocationEventHandler.class)) {
            log.warn("Unable to find a classloader with access to both the service interface {} and Tritium. "
                    + "Delegate {} of type {} will not be instrumented",
                    SafeArg.of("interface", interfaceClass),
                    UnsafeArg.of("delegate", delegate),
                    SafeArg.of("delegateType", delegate.getClass()));
            return delegate;
        }
        @SuppressWarnings("unchecked") ImmutableList<Class<?>> additionalInterfaces = getAdditionalInterfaces(
                classLoader, interfaceClass, (Class<? extends U>) delegate.getClass());

        try {
            return newInstrumentationClass(classLoader, interfaceClass, additionalInterfaces)
                    .getConstructor(interfaceClass, InvocationEventHandler.class, InstrumentationFilter.class)
                    .newInstance(delegate, CompositeInvocationEventHandler.of(handlers), instrumentationFilter);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Failed to instrument interface {}. Delegate {} of type {} will not be instrumented",
                    SafeArg.of("interface", interfaceClass),
                    UnsafeArg.of("delegate", delegate),
                    SafeArg.of("delegateType", delegate.getClass()),
                    e);
            return delegate;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T> newInstrumentationClass(ClassLoader classLoader,
            Class<T> interfaceClass, ImmutableList<Class<?>> additionalInterfaces) {
        checkNotNull(classLoader, "classLoader");
        checkNotNull(interfaceClass, "interfaceClass");
        checkNotNull(additionalInterfaces, "additionalInterfaces");
        checkArgument(!additionalInterfaces.contains(interfaceClass),
                "additionalInterfaces must not contain interfaceClass",
                SafeArg.of("additionalInterfaces", additionalInterfaces),
                SafeArg.of("interfaceClass", interfaceClass));
        ImmutableList<Class<?>> interfaces = ImmutableList.<Class<?>>builder()
                .add(interfaceClass)
                .addAll(additionalInterfaces)
                .build();
        return (Class<? extends T>) cache.findOrInsert(classLoader, interfaces, () -> {
            DynamicType.Builder.MethodDefinition.ReceiverTypeDefinition<Object> builder =
                    new ByteBuddy(ClassFileVersion.ofThisVm(ClassFileVersion.JAVA_V8))
                            .subclass(Object.class)
                            .modifiers(Modifier.FINAL | Modifier.PUBLIC)
                            .name(className(interfaces))
                            .defineConstructor(Visibility.PUBLIC)
                            .withParameters(interfaceClass, InvocationEventHandler.class, InstrumentationFilter.class)
                            .intercept(MethodCall.invoke(Object.class.getDeclaredConstructor())
                                    .andThen(FieldAccessor.ofField("delegate").setsArgumentAt(0))
                                    .andThen(FieldAccessor.ofField("invocationEventHandler").setsArgumentAt(1))
                                    .andThen(FieldAccessor.ofField("instrumentationFilter").setsArgumentAt(2)))
                            .implement(interfaces)
                            .method(ElementMatchers.isToString())
                            .intercept(MethodCall.invokeSelf().onField("delegate"));
            List<Method> allMethods = new ArrayList<>();
            for (Class<?> iface : interfaces) {
                boolean allowDirectAccess = iface.isAssignableFrom(interfaceClass);
                for (Method method : iface.getMethods()) {
                    int index = allMethods.size();
                    allMethods.add(method);
                    // Retain tritium proxy detail where hashcode, equals, and toString cannot be instrumented.
                    builder = builder.method(ElementMatchers.not(ElementMatchers.isHashCode()
                            .or(ElementMatchers.isEquals())
                            .or(ElementMatchers.isToString()))
                            .and(ElementMatchers.is(method)))
                            .intercept(Advice.withCustomMapping()
                                    .bind(ByteBuddyInstrumentationAdvice.MethodIndex.class, index)
                                    .to(ByteBuddyInstrumentationAdvice.class)
                                    .wrap(allowDirectAccess
                                            ? MethodCall.invokeSelf().onField("delegate").withAllArguments()
                                            : MethodCall.invokeSelf()
                                                    // Byte buddy doesn't seem to allow casting from fields, but
                                                    // we can cast the result of a trivial call (in this case
                                                    // Objects.requireNonNull) into the desired type.
                                                    .onMethodCall(passThroughMethod().withField("delegate"))
                                                    .withAllArguments()
                                                    .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC)));
                }
            }
            return builder
                    .defineField("delegate", interfaceClass,
                            Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)
                    .defineField("invocationEventHandler", InvocationEventHandler.class,
                            Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)
                    .defineField("instrumentationFilter", InstrumentationFilter.class,
                            Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)
                    .defineField(METHODS_FIELD, Method[].class, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                    .defineField(LOGGER_FIELD, Logger.class, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                    .defineField(DISABLED_HANDLER_SENTINEL_FIELD, InvocationContext.class,
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                    .initializer(new StaticFieldLoadedTypeInitializer(METHODS_FIELD, allMethods.toArray(new Method[0])))
                    .initializer(new StaticFieldLoadedTypeInitializer(
                            DISABLED_HANDLER_SENTINEL_FIELD, DisabledHandlerSentinel.INSTANCE))
                    .initializer(LoggerInitializer.INSTANCE)
                    .make()
                    .load(classLoader)
                    .getLoaded();
        });
    }

    private static MethodCall.WithoutSpecifiedTarget passThroughMethod() throws NoSuchMethodException {
        return MethodCall.invoke(Objects.class.getMethod("requireNonNull", Object.class));
    }

    private static <T, U extends T> ImmutableList<Class<?>> getAdditionalInterfaces(
            ClassLoader classLoader, Class<T> interfaceClass, Class<U> delegateClass) {
        Class<?>[] discoveredInterfaces = Proxies.interfaces(interfaceClass, delegateClass);
        ImmutableList.Builder<Class<?>> additionalInterfaces =
                ImmutableList.builderWithExpectedSize(discoveredInterfaces.length - 1);
        checkState(interfaceClass.equals(discoveredInterfaces[0]), "Expected the provided interface first");
        for (int i = 1; i < discoveredInterfaces.length; i++) {
            Class<?> additionalInterface = discoveredInterfaces[i];
            if (assignableFromAny(additionalInterface, discoveredInterfaces)) {
                // No need to retain interfaces already provided by the requested interface class
                continue;
            }
            if (isAccessibleFrom(classLoader, additionalInterface)) {
                additionalInterfaces.add(additionalInterface);
            } else {
                log.debug("Instrumented service of type {} cannot implement {} because the interface is not accessible",
                        SafeArg.of("delegateType", delegateClass),
                        SafeArg.of("inaccessibleInterface", additionalInterface));
            }
        }
        return additionalInterfaces.build();
    }

    private static boolean assignableFromAny(Class<?> target, Class<?>[] allInterfaces) {
        for (Class<?> toCheck : allInterfaces) {
            // allInterfaces always contains target
            if (toCheck != target && target.isAssignableFrom(toCheck)) {
                return true;
            }
        }
        return false;
    }

    private static ClassLoader getClassLoader(Class<?> clazz) {
        ClassLoader serviceClassLoader = getServiceClassLoader(clazz);
        ClassLoader instrumentationClassLoader = ByteBuddyInstrumentation.class.getClassLoader();
        if (Objects.equals(instrumentationClassLoader, serviceClassLoader)) {
            return instrumentationClassLoader;
        }
        return isClassLoadable(instrumentationClassLoader, clazz)
                // Prefer the instrumentation classloader when it's broader than the services classloader
                // to avoid problems accessing tritium from generated instrumentation, and for better
                // generated class caching.
                ? instrumentationClassLoader
                // However in some scenarios, the instrumentationClassLoader does not have access to the
                // service interface, so we must use the serviceClassLoader.
                : serviceClassLoader;
    }

    private static ClassLoader getServiceClassLoader(Class<?> clazz) {
        checkNotNull(clazz, "Class is required");
        ClassLoader loader = clazz.getClassLoader();
        // Some internal classes return a null classloader, in these cases we provide the instrumentation loader.
        if (loader == null) {
            // No reason to broaden beyond the instrumentation classloader because instrumentation
            ClassLoader instrumentationClassLoader = ByteBuddyInstrumentation.class.getClassLoader();
            if (instrumentationClassLoader != null && isClassLoadable(instrumentationClassLoader, clazz)) {
                return instrumentationClassLoader;
            }
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null && isClassLoadable(contextClassLoader, clazz)) {
                return contextClassLoader;
            }
            throw new SafeIllegalStateException("Failed to find a classloader for class",
                    SafeArg.of("class", clazz));
        }
        return loader;
    }

    private static boolean isAccessible(Class<?> clazz) {
        checkNotNull(clazz, "Class is required");
        return Modifier.isPublic(clazz.getModifiers())
                // If there is an enclosing class, it must also be accessible
                && (clazz.getEnclosingClass() == null || isAccessible(clazz.getEnclosingClass()));
    }

    /** Detect if clazz is reachable using the provided loader. */
    private static boolean isAccessibleFrom(ClassLoader loader, Class<?> clazz) {
        checkNotNull(loader, "ClassLoader is required");
        checkNotNull(clazz, "Class is required");
        if (!isAccessible(clazz)) {
            return false;
        }
        return isClassLoadable(loader, clazz);
    }

    private static boolean isClassLoadable(ClassLoader loader, Class<?> clazz) {
        checkNotNull(loader, "ClassLoader is required");
        checkNotNull(clazz, "Class is required");
        // Fast check to avoid potentially slow loading
        if (loader.equals(clazz.getClassLoader())) {
            return true;
        }
        try {
            return clazz.equals(loader.loadClass(clazz.getName()));
        } catch (ReflectiveOperationException | Error e) {
            return false;
        }
    }

    private static final class StaticFieldLoadedTypeInitializer implements LoadedTypeInitializer {

        private final String fieldName;
        private final Object value;

        StaticFieldLoadedTypeInitializer(String fieldName, Object value) {
            this.fieldName = fieldName;
            this.value = value;
        }

        @Override
        public void onLoad(Class<?> type) {
            try {
                type.getDeclaredField(fieldName).set(null, value);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new SafeIllegalStateException("Failed to set static field", e, SafeArg.of("field", fieldName));
            }
        }

        @Override
        public boolean isAlive() {
            return true;
        }
    }

    private enum LoggerInitializer implements LoadedTypeInitializer {
        INSTANCE;

        @Override
        public void onLoad(Class<?> type) {
            try {
                type.getDeclaredField(LOGGER_FIELD).set(null, LoggerFactory.getLogger(type));
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new SafeIllegalStateException("Failed to set the logger field", e);
            }
        }

        @Override
        public boolean isAlive() {
            return true;
        }
    }

    private static String className(List<Class<?>> interfaceClasses) {
        return "com.palantir.tritium.proxy.Instrumented"
                + UNDERSCORE_JOINER.join(Lists.transform(interfaceClasses, Class::getSimpleName))
                + '$' + offset.getAndIncrement();
    }

    // A sentinel value is used to differentiate null contexts returned by handlers from
    // invocations on disabled handlers.
    private enum DisabledHandlerSentinel implements InvocationContext {
        INSTANCE;

        @Override
        public long getStartTimeNanos() {
            throw fail();
        }

        @Nullable
        @Override
        public Object getInstance() {
            throw fail();
        }

        @Override
        public Method getMethod() {
            throw fail();
        }

        @Override
        public Object[] getArgs() {
            throw fail();
        }

        private static RuntimeException fail() {
            throw new UnsupportedOperationException("methods should not be invoked");
        }
    }
}
