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

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.CompositeInvocationEventHandler;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.TypeCache;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
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
        @SuppressWarnings("unchecked") ImmutableList<Class<?>> additionalInterfaces = getAdditionalInterfaces(
                classLoader, interfaceClass, (Class<? extends U>) delegate.getClass());

        try {
            return newInstrumentationClass(classLoader, interfaceClass, additionalInterfaces)
                    .getConstructor(interfaceClass, InvocationEventHandler.class, InstrumentationFilter.class)
                    .newInstance(delegate, CompositeInvocationEventHandler.of(handlers), instrumentationFilter);
        } catch (ReflectiveOperationException e) {
            throw new SafeRuntimeException("Failed to instrumented delegate", e);
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
                // If the method is not available on our target interface, add an internal getter to cast the delegate
                // object into the target interface.
                if (!allowDirectAccess) {
                    builder = builder.defineMethod(delegateMethod(iface), iface, Modifier.PRIVATE | Modifier.FINAL)
                            .intercept(FieldAccessor.ofField("delegate")
                                    .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC));
                }
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
                                            ? MethodDelegation.toField("delegate")
                                            : MethodDelegation.toMethodReturnOf(delegateMethod(iface))));
                }
            }
            return builder
                    .defineField("delegate", interfaceClass,
                            Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)
                    .defineField("invocationEventHandler", InvocationEventHandler.class,
                            Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)
                    .defineField("instrumentationFilter", InstrumentationFilter.class,
                            Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)
                    .defineField("methods", Method[].class, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                    .initializer(new StaticFieldLoadedTypeInitializer("methods", allMethods.toArray(new Method[0])))
                    .make()
                    .load(classLoader)
                    .getLoaded();
        });
    }

    private static <T, U extends T> ImmutableList<Class<?>> getAdditionalInterfaces(
            ClassLoader classLoader, Class<T> interfaceClass, Class<U> delegateClass) {
        Class<?>[] discoveredInterfaces = Proxies.interfaces(interfaceClass, delegateClass);
        ImmutableList.Builder<Class<?>> additionalInterfaces =
                ImmutableList.builderWithExpectedSize(discoveredInterfaces.length - 1);
        checkState(interfaceClass.equals(discoveredInterfaces[0]), "Expected the provided interface first");
        for (int i = 1; i < discoveredInterfaces.length; i++) {
            Class<?> additionalInterface = discoveredInterfaces[i];
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

    private static ClassLoader getClassLoader(Class<?> clazz) {
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
        // Fast check to avoid potentially slow loading
        if (loader.equals(clazz.getClassLoader())) {
            return true;
        }
        return isClassLoadable(loader, clazz);
    }

    private static boolean isClassLoadable(ClassLoader loader, Class<?> clazz) {
        checkNotNull(loader, "ClassLoader is required");
        checkNotNull(clazz, "Class is required");
        try {
            return clazz.equals(loader.loadClass(clazz.getName()));
        } catch (ReflectiveOperationException | Error e) {
            return false;
        }
    }

    private static final CharMatcher ILLEGAL_METHOD_CHARS = CharMatcher.inRange('a', 'z')
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .negate()
            .precomputed();

    private static String delegateMethod(Class<?> iface) {
        checkNotNull(iface, "Interface class is required");
        return "instrumentation_delegateAs" + ILLEGAL_METHOD_CHARS.replaceFrom(iface.getName(), '_');
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

    private static String className(List<Class<?>> interfaceClasses) {
        return "com.palantir.tritium.proxy.Instrumented"
                + UNDERSCORE_JOINER.join(Lists.transform(interfaceClasses, Class::getSimpleName))
                + '$' + offset.getAndIncrement();
    }
}
