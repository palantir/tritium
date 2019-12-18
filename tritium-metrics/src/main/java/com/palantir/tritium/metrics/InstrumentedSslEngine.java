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

package com.palantir.tritium.metrics;

import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InstrumentedSslEngine extends SSLEngine {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedSslEngine.class);

    // n.b. This value is set using 'beginHandshake' for renegotiation. We instrument both because ciphers may change.
    private final AtomicBoolean handshaking = new AtomicBoolean(true);
    private final SSLEngine engine;
    private final TlsMetrics metrics;
    private final String name;

    static SSLEngine instrument(SSLEngine engine, TlsMetrics metrics, String name) {
        Method getApplicationProtocol = getMethodNullable(engine.getClass(), "getApplicationProtocol");
        // Avoid the other three lookups if methods aren't present
        if (getApplicationProtocol != null) {
            Method getHandshakeApplicationProtocol = getMethodNullable(
                    engine.getClass(), "getHandshakeApplicationProtocol");
            Method setHandshakeApplicationProtocolSelector = getMethodNullable(
                    engine.getClass(), "setHandshakeApplicationProtocolSelector", BiFunction.class);
            Method getHandshakeApplicationProtocolSelector = getMethodNullable(
                    engine.getClass(), "getHandshakeApplicationProtocolSelector");
            if (getHandshakeApplicationProtocol != null
                    && setHandshakeApplicationProtocolSelector != null
                    && getHandshakeApplicationProtocolSelector != null) {
                return new InstrumentedSslEngineJava9(engine, metrics, name,
                        getApplicationProtocol,
                        getHandshakeApplicationProtocol,
                        setHandshakeApplicationProtocolSelector,
                        getHandshakeApplicationProtocolSelector);
            }
        }
        return new InstrumentedSslEngine(engine, metrics, name);
    }

    // Extracts a delegate SSLEngine instance if the input is wrapped.
    static SSLEngine extractDelegate(SSLEngine maybeInstrumented) {
        SSLEngine current = maybeInstrumented;
        while (current instanceof InstrumentedSslEngine) {
            current = ((InstrumentedSslEngine) current).engine;
        }
        return current;
    }

    @Nullable
    private static Method getMethodNullable(Class<? extends SSLEngine> target, String name, Class<?>... paramTypes) {
        if (System.getSecurityManager() == null) {
            return getMethodNullableInternal(target, name, paramTypes);
        } else {
            return AccessController.doPrivileged(
                    (PrivilegedAction<Method>) () -> getMethodNullableInternal(target, name, paramTypes));
        }
    }

    @Nullable
    private static Method getMethodNullableInternal(
            Class<? extends SSLEngine> target, String name, Class<?>... paramTypes) {
        try {
            Method method = target.getMethod(name, paramTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private InstrumentedSslEngine(SSLEngine engine, TlsMetrics metrics, String name) {
        this.engine = engine;
        this.metrics = metrics;
        this.name = name;
    }

    @Override
    public String getPeerHost() {
        return engine.getPeerHost();
    }

    @Override
    public int getPeerPort() {
        return engine.getPeerPort();
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return check(engine.wrap(src, dst));
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] sources, ByteBuffer byteBuffer) throws SSLException {
        return check(engine.wrap(sources, byteBuffer));
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] sources, int offset, int length, ByteBuffer dest) throws SSLException {
        return check(engine.wrap(sources, offset, length, dest));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer byteBuffer1) throws SSLException {
        return check(engine.unwrap(byteBuffer, byteBuffer1));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer[] byteBuffers) throws SSLException {
        return check(engine.unwrap(byteBuffer, byteBuffers));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
        return check(engine.unwrap(src, dsts, offset, length));
    }

    @Override
    public Runnable getDelegatedTask() {
        return engine.getDelegatedTask();
    }

    @Override
    public void closeInbound() throws SSLException {
        engine.closeInbound();
    }

    @Override
    public boolean isInboundDone() {
        return engine.isInboundDone();
    }

    @Override
    public void closeOutbound() {
        engine.closeOutbound();
    }

    @Override
    public boolean isOutboundDone() {
        return engine.isOutboundDone();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return engine.getSupportedCipherSuites();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return engine.getEnabledCipherSuites();
    }

    @Override
    public void setEnabledCipherSuites(String[] strings) {
        engine.setEnabledCipherSuites(strings);
    }

    @Override
    public String[] getSupportedProtocols() {
        return engine.getSupportedProtocols();
    }

    @Override
    public String[] getEnabledProtocols() {
        return engine.getEnabledProtocols();
    }

    @Override
    public void setEnabledProtocols(String[] strings) {
        engine.setEnabledProtocols(strings);
    }

    @Override
    public SSLSession getSession() {
        return engine.getSession();
    }

    @Override
    public SSLSession getHandshakeSession() {
        return engine.getHandshakeSession();
    }

    @Override
    public void beginHandshake() throws SSLException {
        engine.beginHandshake();
        handshaking.set(true);
    }

    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return engine.getHandshakeStatus();
    }

    @Override
    public void setUseClientMode(boolean mode) {
        engine.setUseClientMode(mode);
    }

    @Override
    public boolean getUseClientMode() {
        return engine.getUseClientMode();
    }

    @Override
    public void setNeedClientAuth(boolean need) {
        engine.setNeedClientAuth(need);
    }

    @Override
    public boolean getNeedClientAuth() {
        return engine.getNeedClientAuth();
    }

    @Override
    public void setWantClientAuth(boolean want) {
        engine.setWantClientAuth(want);
    }

    @Override
    public boolean getWantClientAuth() {
        return engine.getWantClientAuth();
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        engine.setEnableSessionCreation(flag);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return engine.getEnableSessionCreation();
    }

    @Override
    public SSLParameters getSSLParameters() {
        return engine.getSSLParameters();
    }

    @Override
    public void setSSLParameters(SSLParameters sslParameters) {
        engine.setSSLParameters(sslParameters);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name=" + name + ", delegate=" + engine + '}';
    }

    private SSLEngineResult check(SSLEngineResult result) {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED
                && handshaking.compareAndSet(true, false)) {
            try {
                SSLSession session = engine.getSession();
                if (session != null) {
                    metrics.handshake()
                            .context(name)
                            .cipher(session.getCipherSuite())
                            .protocol(session.getProtocol())
                            .build()
                            .mark();
                }
            } catch (RuntimeException e) {
                log.warn("Failed to record handshake metrics", e);
            }
        }
        return result;
    }

    private static final class InstrumentedSslEngineJava9 extends InstrumentedSslEngine {

        private final SSLEngine engine;
        private final Method getApplicationProtocol;
        private final Method getHandshakeApplicationProtocol;
        private final Method setHandshakeApplicationProtocolSelector;
        private final Method getHandshakeApplicationProtocolSelector;

        private InstrumentedSslEngineJava9(SSLEngine engine, TlsMetrics metrics, String name,
                Method getApplicationProtocol, Method getHandshakeApplicationProtocol,
                Method setHandshakeApplicationProtocolSelector, Method getHandshakeApplicationProtocolSelector) {
            super(engine, metrics, name);
            this.engine = engine;
            this.getApplicationProtocol = getApplicationProtocol;
            this.getHandshakeApplicationProtocol = getHandshakeApplicationProtocol;
            this.setHandshakeApplicationProtocolSelector = setHandshakeApplicationProtocolSelector;
            this.getHandshakeApplicationProtocolSelector = getHandshakeApplicationProtocolSelector;
        }

        // Override(java9+)
        @SuppressWarnings("unused")
        public String getApplicationProtocol() {
            try {
                return (String) getApplicationProtocol.invoke(engine);
            } catch (ReflectiveOperationException e) {
                throw new SafeIllegalStateException("Failed to invoke getApplicationProtocol", e);
            }
        }

        // Override(java9+)
        @SuppressWarnings("unused")
        public String getHandshakeApplicationProtocol() {
            try {
                return (String) getHandshakeApplicationProtocol.invoke(engine);
            } catch (ReflectiveOperationException e) {
                throw new SafeIllegalStateException("Failed to invoke getHandshakeApplicationProtocol", e);
            }
        }

        // Override(java9+)
        @SuppressWarnings("unused")
        public void setHandshakeApplicationProtocolSelector(BiFunction<SSLEngine, List<String>, String> selector) {
            try {
                setHandshakeApplicationProtocolSelector.invoke(engine, selector);
            } catch (ReflectiveOperationException e) {
                throw new SafeIllegalStateException("Failed to invoke setHandshakeApplicationProtocolSelector", e);
            }
        }

        // Override(java9+)
        @SuppressWarnings({"NoFunctionalReturnType", "unchecked", "unused"})
        public BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector() {
            try {
                return (BiFunction<SSLEngine, List<String>, String>)
                        getHandshakeApplicationProtocolSelector.invoke(engine);
            } catch (ReflectiveOperationException e) {
                throw new SafeIllegalStateException("Failed to invoke getHandshakeApplicationProtocolSelector", e);
            }
        }
    }
}
