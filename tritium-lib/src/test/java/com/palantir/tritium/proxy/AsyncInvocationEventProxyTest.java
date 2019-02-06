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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class AsyncInvocationEventProxyTest {
    private static final String result = "Result";

    @Mock private AsyncIface delegate;
    @Mock private SimpleInvocationEventHandler handler;
    private AsyncIface proxy;

    @Before
    public void before() {
        proxy = Instrumentation.builder(AsyncIface.class, delegate)
                .withHandler(handler)
                .build();
        when(handler.isEnabled()).thenCallRealMethod();
        when(handler.preInvocation(any(), any(), any())).thenCallRealMethod();
    }

    @Test
    public void testCfSuccess() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        when(delegate.cf()).thenReturn(future);

        CompletableFuture<String> ret = proxy.cf();
        assertThat(ret.isDone()).isFalse();
        verify(handler, never()).onSuccess(any(), any());

        future.complete(result);
        assertThat(ret.get()).isEqualTo(result);
        verify(handler).onSuccess(any(), eq(result));
    }

    @Test
    public void testCfThrows() {
        RuntimeException exception = new RuntimeException();
        CompletableFuture<String> future = new CompletableFuture<>();
        when(delegate.cf()).thenReturn(future);

        CompletableFuture<String> ret = proxy.cf();
        assertThat(ret.isDone()).isFalse();
        verify(handler, never()).onSuccess(any(), any());

        future.completeExceptionally(exception);
        assertThat(ret.isCompletedExceptionally()).isTrue();
        verify(handler).onFailure(any(), eq(exception));
    }

    @Test
    public void testLfSuccess() {
        SettableFuture<String> future = SettableFuture.create();
        when(delegate.lf()).thenReturn(future);

        ListenableFuture<String> ret = proxy.lf();
        assertThat(ret.isDone()).isFalse();
        verify(handler, never()).onSuccess(any(), any());

        future.set(result);
        assertThat(ret.isDone()).isTrue();
        verify(handler).onSuccess(any(), eq(result));
    }

    @Test
    public void testLfThrows() {
        RuntimeException exception = new RuntimeException();
        SettableFuture<String> future = SettableFuture.create();
        when(delegate.lf()).thenReturn(future);

        ListenableFuture<String> ret = proxy.lf();
        assertThat(ret.isDone()).isFalse();
        verify(handler, never()).onFailure(any(), any());

        future.setException(exception);
        assertThat(ret.isDone()).isTrue();
        verify(handler).onFailure(any(), eq(exception));
    }

    interface AsyncIface {
        CompletableFuture<String> cf();

        ListenableFuture<String> lf();
    }

    interface SimpleInvocationEventHandler extends InvocationEventHandler<InvocationContext> {
        @Override
        default boolean isEnabled() {
            return true;
        }

        @Override
        default InvocationContext preInvocation(Object instance, Method method, Object[] args) {
            return DefaultInvocationContext.of(instance, method, args);
        }
    }
}
