/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.event;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class CompositeInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private final InvocationEventHandler<InvocationContext>[] handlers;

    @SuppressWarnings("unchecked")
    private CompositeInvocationEventHandler(List<InvocationEventHandler<InvocationContext>> handlers) {
        this.handlers = checkNotNull(handlers, "handlers").toArray(new InvocationEventHandler[0]);
        for (InvocationEventHandler<InvocationContext> handler : handlers) {
            checkNotNull(handler, "Null handlers are not allowed");
        }
    }

    public static InvocationEventHandler<InvocationContext> of(
            List<InvocationEventHandler<InvocationContext>> handlers) {
        if (handlers.isEmpty()) {
            return NoOpInvocationEventHandler.INSTANCE;
        } else if (handlers.size() == 1) {
            return checkNotNull(handlers.get(0), "Null handlers are not allowed");
        } else {
            return new CompositeInvocationEventHandler(handlers);
        }
    }

    @Override
    public InvocationContext preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        InvocationContext[] contexts = new InvocationContext[handlers.length];

        for (int i = 0; i < handlers.length; i++) {
            contexts[i] = Handlers.preWithEnabledCheck(
                    handlers[i], InstrumentationFilters.INSTRUMENT_ALL, instance, method, args);
        }

        return new CompositeInvocationContext(instance, method, args, contexts);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        debugIfNullContext(context);
        if (context != null) {
            success(((CompositeInvocationContext) context).getContexts(), result);
        }
    }

    private void success(@Nonnull InvocationContext[] contexts, @Nullable Object result) {
        for (int i = contexts.length - 1; i > -1; i--) {
            Handlers.onSuccess(handlers[i], contexts[i], result);
        }
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        debugIfNullContext(context);
        if (context != null) {
            failure(((CompositeInvocationContext) context).getContexts(), cause);
        }
    }

    private void failure(InvocationContext[] contexts, @Nonnull Throwable cause) {
        for (int i = contexts.length - 1; i > -1; i--) {
            Handlers.onFailure(handlers[i], contexts[i], cause);
        }
    }

    @Override
    public String toString() {
        return "CompositeInvocationEventHandler{handlers=" + Arrays.toString(handlers) + '}';
    }

    static class CompositeInvocationContext extends DefaultInvocationContext {

        private final InvocationContext[] contexts;

        CompositeInvocationContext(
                Object instance, Method method, @Nullable Object[] args, InvocationContext[] contexts) {
            super(System.nanoTime(), instance, method, args);
            this.contexts = checkNotNull(contexts);
        }

        InvocationContext[] getContexts() {
            return contexts;
        }
    }
}
