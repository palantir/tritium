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

package com.palantir.tritium.microbenchmarks;

import com.palantir.logsafe.Preconditions;
import com.palantir.tritium.v1.api.event.InvocationContext;
import com.palantir.tritium.v1.api.event.InvocationEventHandler;
import com.palantir.tritium.v1.core.event.DefaultInvocationContext;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.openjdk.jmh.infra.Blackhole;

final class BlackholeInvocationEventHandler implements InvocationEventHandler<InvocationContext> {

    private static final InvocationContext SINGLETON_CONTEXT =
            DefaultInvocationContext.of("stub", String.class.getMethods()[0], new Object[0]);

    private final Blackhole blackhole;

    BlackholeInvocationEventHandler(Blackhole blackhole) {
        this.blackhole = Preconditions.checkNotNull(blackhole, "Blackhole is required");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public InvocationContext preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        consume(instance, method, args);
        return SINGLETON_CONTEXT;
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        consume(context, result);
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        consume(context, cause);
    }

    private void consume(@Nullable Object obj0, @Nullable Object obj1, @Nullable Object obj2) {
        consume(obj0, obj1);
        blackhole.consume(obj2);
    }

    private void consume(@Nullable Object obj0, @Nullable Object obj1) {
        blackhole.consume(obj0);
        blackhole.consume(obj1);
    }
}
