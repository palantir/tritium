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

package com.palantir.tritium.metrics.tls;

import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
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

final class InstrumentedSslEngine extends SSLEngine {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedSslEngine.class);

    private final AtomicBoolean handshaking = new AtomicBoolean();
    private final SSLEngine engine;
    private final TaggedMetricRegistry metrics;
    private final String name;

    InstrumentedSslEngine(SSLEngine engine, TaggedMetricRegistry metrics, String name) {
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
        return instrument(engine.wrap(src, dst));
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] sources, ByteBuffer byteBuffer) throws SSLException {
        return instrument(engine.wrap(sources, byteBuffer));
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] sources, int offset, int length, ByteBuffer dest) throws SSLException {
        return instrument(engine.wrap(sources, offset, length, dest));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer byteBuffer1) throws SSLException {
        return instrument(engine.unwrap(byteBuffer, byteBuffer1));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer[] byteBuffers) throws SSLException {
        return instrument(engine.unwrap(byteBuffer, byteBuffers));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
        return instrument(engine.unwrap(src, dsts, offset, length));
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

    // JAVA 9

    @Nullable
    private static final Method getApplicationProtocol = engineMethodNullable("getApplicationProtocol");
    @Nullable
    private static final Method getHandshakeApplicationProtocol =
            engineMethodNullable("getHandshakeApplicationProtocol");
    @Nullable
    private static final Method setHandshakeApplicationProtocolSelector =
            engineMethodNullable("setHandshakeApplicationProtocolSelector", BiFunction.class);
    @Nullable
    private static final Method getHandshakeApplicationProtocolSelector =
            engineMethodNullable("getHandshakeApplicationProtocolSelector");

    // Override(java9+)
    public String getApplicationProtocol() {
        if (getApplicationProtocol == null) {
            throw new SafeIllegalStateException("getApplicationProtocol is not supported");
        }
        try {
            return (String) getApplicationProtocol.invoke(engine);
        } catch (ReflectiveOperationException e) {
            throw new SafeIllegalStateException("Failed to invoke getApplicationProtocol", e);
        }
    }

    // Override(java9+)
    public String getHandshakeApplicationProtocol() {
        if (getHandshakeApplicationProtocol == null) {
            throw new SafeIllegalStateException("getHandshakeApplicationProtocol is not supported");
        }
        try {
            return (String) getHandshakeApplicationProtocol.invoke(engine);
        } catch (ReflectiveOperationException e) {
            throw new SafeIllegalStateException("Failed to invoke getHandshakeApplicationProtocol", e);
        }
    }

    // Override(java9+)
    public void setHandshakeApplicationProtocolSelector(BiFunction<SSLEngine, List<String>, String> selector) {
        if (setHandshakeApplicationProtocolSelector == null) {
            throw new SafeIllegalStateException("setHandshakeApplicationProtocolSelector is not supported");
        }
        try {
            setHandshakeApplicationProtocolSelector.invoke(engine, selector);
        } catch (ReflectiveOperationException e) {
            throw new SafeIllegalStateException("Failed to invoke setHandshakeApplicationProtocolSelector", e);
        }
    }

    // Override(java9+)
    @SuppressWarnings("unchecked")
    public BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector() {
        if (getHandshakeApplicationProtocolSelector == null) {
            throw new SafeIllegalStateException("getHandshakeApplicationProtocolSelector is not supported");
        }
        try {
            return (BiFunction<SSLEngine, List<String>, String>)
                    getHandshakeApplicationProtocolSelector.invoke(engine);
        } catch (ReflectiveOperationException e) {
            throw new SafeIllegalStateException("Failed to invoke getHandshakeApplicationProtocolSelector", e);
        }
    }

    @Nullable
    private static Method engineMethodNullable(String name, Class<?>... paramTypes) {
        try {
            return SSLEngine.class.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "InstrumentedSSLEngine{name=" + name + ", delegate=" + engine + '}';
    }

    private SSLEngineResult instrument(SSLEngineResult result) {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED
                && handshaking.compareAndSet(true, false)) {
            try {
                SSLSession session = engine.getHandshakeSession();
                metrics.meter(MetricName.builder()
                        .safeName("tls.handshake")
                        .putSafeTags("name", name)
                        .putSafeTags("cipher", session.getCipherSuite())
                        .putSafeTags("protocol", session.getProtocol())
                        .putSafeTags("type", "engine")
                        .build())
                        .mark();
            } catch (RuntimeException e) {
                log.warn("Failed to record handshake metrics", e);
            }
        }
        return result;
    }
}
