/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.tritium.metrics.jvm.InternalJvmMetrics.InternalJvm_NativeImage;

public final class GraalImageInfo {

    /**
     * Ported over from {@code org.graalvm.nativeimage.ImageInfo},
     * so that we do not have to pull the entire graal-sdk jar at runtime.
     */
    private static final String PROPERTY_IMAGE_CODE_KEY = "org.graalvm.nativeimage.imagecode";

    public static final InternalJvm_NativeImage NATIVE = System.getProperty(PROPERTY_IMAGE_CODE_KEY) != null
            ? InternalJvm_NativeImage.TRUE
            : InternalJvm_NativeImage.FALSE;

    private GraalImageInfo() {}
}
