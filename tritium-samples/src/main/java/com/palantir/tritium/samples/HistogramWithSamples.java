/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.samples;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Reservoir;
import java.util.List;

public final class HistogramWithSamples extends Histogram {

    private final Sampler sampler;

    public HistogramWithSamples(Reservoir reservoir, Sampler sampler) {
        super(reservoir);
        this.sampler = sampler;
    }

    @Override
    public void update(long value) {
        super.update(value);
        sampler.observe(value);
    }

    public List<Sample> getSamples() {
        return sampler.collect();
    }
}
