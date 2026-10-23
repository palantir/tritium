/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.metrics.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class NegligibleSamplesTest {

    private static final double NEGLIGIBLE = NegligibleSamples.RELATIVE_WEIGHT_THRESHOLD;
    private static final double BUDGET = NegligibleSamples.EVICTED_WEIGHT_BUDGET;

    @Test
    void evictsEveryNegligibleSampleWithinBudget() {
        // one heavy sample and a few negligible ones, far inside the budget
        double[] weights = {1e-9, 1, 2e-9, 3e-9};
        assertThat(threshold(weights, 0)).isEqualTo(NEGLIGIBLE);
    }

    @Test
    void evictsNothingWithoutNegligibleSamples() {
        assertThat(threshold(new double[] {0.5, 1, 0.9}, 0)).isZero();
    }

    @Test
    void keepsAnOldBurstThatStillCarriesWeight() {
        // 1,027 equal old samples next to one newer one: together 0.1% of the weight, a hundred times the budget
        double[] weights = new double[1028];
        Arrays.fill(weights, 1);
        weights[1027] = 1.0145e6;
        assertThat(evicted(weights, 0)).isZero();
    }

    @Test
    void evictsOnlyTheLightestThatFitTheBudget() {
        double[] weights = new double[1001];
        for (int i = 0; i < 1000; i++) {
            weights[i] = 1 + i * 1e-3;
        }
        weights[1000] = 2e6;
        double total = Arrays.stream(weights).sum();
        double removed = evicted(weights, 0);
        assertThat(removed).isPositive().isLessThanOrEqualTo(BUDGET * total);
        // the next lightest wouldn't have fit
        double threshold = threshold(weights, 0);
        assertThat(removed + threshold).isGreaterThan(BUDGET * total);
    }

    @Test
    void countsWhatWasAlreadyEvicted() {
        double[] weights = {1e-9, 1};
        double total = Arrays.stream(weights).sum();
        assertThat(threshold(weights, BUDGET * total * 0.5)).isEqualTo(NEGLIGIBLE);
        // the reservoir already evicted its whole budget
        assertThat(threshold(weights, BUDGET * total * 2)).isZero();
    }

    private static double threshold(double[] weights, double evicted) {
        double max = Arrays.stream(weights).max().orElseThrow();
        double total = Arrays.stream(weights).sum();
        return NegligibleSamples.evictionThreshold(weights, weights.length, max, total, evicted);
    }

    private static double evicted(double[] weights, double evicted) {
        double threshold = threshold(weights, evicted);
        return Arrays.stream(weights).filter(weight -> weight < threshold).sum();
    }
}
