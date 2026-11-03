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

import com.codahale.metrics.Clock;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;
import java.time.Duration;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tritium's decaying reservoirs against dropwizard's {@link com.codahale.metrics.LockFreeExponentiallyDecayingReservoir},
 * fed identical updates on one clock and snapshotted every 30 seconds like a metric reporter.
 *
 * <p>The reservoirs are large enough never to fill, so both keep exactly the same samples apart from the ones tritium
 * evicts, and any difference is eviction's. Everything evicted is at most {@link NegligibleSamples#EVICTED_WEIGHT_BUDGET}
 * of the weight, {@code m}, so each quantile must lie between dropwizard's at {@code q - m / (1 - m)} and
 * {@code q + m / (1 - m)}, the mean within {@code m / (1 - m)} of the value range, the standard deviation within
 * {@code sqrt(1.5 m)} of it, and the max and min can only narrow.
 */
class DecayingReservoirCompatibilityTest {

    private static final int NEVER_FULL = 200_000;
    private static final double[] QUANTILES = {0.5, 0.75, 0.95, 0.98, 0.99, 0.999};
    private static final double BUDGET = NegligibleSamples.EVICTED_WEIGHT_BUDGET;
    private static final double RANK_SLACK = BUDGET / (1 - BUDGET) * 1.01;
    private static final long REPORT_INTERVAL = TimeUnit.SECONDS.toNanos(30);

    /** When an update happens and its value; {@code next} returns {@link Long#MAX_VALUE} when there are no more. */
    private interface Workload {
        long next(long afterNanos, SplittableRandom random);

        long value(long nanos, SplittableRandom random);
    }

    static Stream<Arguments> scenarios() {
        List<Arguments> scenarios = List.of(
                Arguments.of("once a minute", Duration.ofHours(8), (Supplier<Workload>)
                        () -> steady(1.0 / 60, lognormal(200, 0.3))),
                Arguments.of("0.3/s", Duration.ofHours(3), (Supplier<Workload>) () -> steady(0.3, lognormal(40, 1.0))),
                Arguments.of("every ~50 s, rare 100x outliers", Duration.ofHours(8), (Supplier<Workload>)
                        () -> steady(0.02, outliers())),
                Arguments.of(
                        "bimodal, 0.05/s", Duration.ofHours(6), (Supplier<Workload>) () -> steady(0.05, bimodal())),
                Arguments.of(
                        "constant value", Duration.ofHours(4), (Supplier<Workload>) () -> steady(0.1, (_t, _r) -> 7)),
                Arguments.of("burst, quiet, then a trickle", Duration.ofHours(8), (Supplier<Workload>)
                        () -> burstThenTrickle()),
                Arguments.of(
                        "10 minute bursts every 2 hours", Duration.ofHours(8), (Supplier<Workload>) () -> bursts()),
                Arguments.of("going quiet", Duration.ofHours(4), (Supplier<Workload>) () -> goingQuiet()));
        return Stream.of(Kind.values())
                .flatMap(kind -> scenarios.stream()
                        .map(scenario -> Arguments.of(kind, scenario.get()[0], scenario.get()[1], scenario.get()[2])));
    }

    enum Kind {
        PLAIN {
            @Override
            Reservoir create(Clock clock) {
                return LockFreeExponentiallyDecayingReservoir.builder()
                        .size(NEVER_FULL)
                        .clock(clock)
                        .build();
            }

            @Override
            Reservoir createDefault(Clock clock) {
                return LockFreeExponentiallyDecayingReservoir.builder()
                        .clock(clock)
                        .build();
            }
        },
        EXEMPLARS {
            @Override
            Reservoir create(Clock clock) {
                return LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                        .size(NEVER_FULL)
                        .clock(clock)
                        .build();
            }

            @Override
            Reservoir createDefault(Clock clock) {
                return LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                        .clock(clock)
                        .build();
            }
        };

        abstract Reservoir create(Clock clock);

        abstract Reservoir createDefault(Clock clock);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("scenarios")
    void staysWithinTheEvictionBoundOfDropwizard(
            Kind kind, String _name, Duration duration, Supplier<Workload> workloads) {
        Workload workload = workloads.get();
        ManualClock clock = new ManualClock();
        Reservoir dropwizard = com.codahale.metrics.LockFreeExponentiallyDecayingReservoir.builder()
                .size(NEVER_FULL)
                .clock(clock)
                .build();
        Reservoir tritium = kind.create(clock);
        SplittableRandom random = new SplittableRandom(42);
        long end = duration.toNanos();
        long nextUpdate = workload.next(0, random);
        int reports = 0;
        int evicting = 0;
        for (long report = REPORT_INTERVAL; report <= end; report += REPORT_INTERVAL) {
            while (nextUpdate <= report) {
                clock.nanos = nextUpdate;
                long value = workload.value(nextUpdate, random);
                dropwizard.update(value);
                tritium.update(value);
                nextUpdate = workload.next(nextUpdate, random);
            }
            clock.nanos = report;
            Snapshot expected = dropwizard.getSnapshot();
            Snapshot actual = tritium.getSnapshot();
            if (expected.size() == 0) {
                assertThat(actual.size()).isZero();
                continue;
            }
            assertWithinBound(actual, expected, report);
            reports++;
            if (actual.size() < expected.size()) {
                evicting++;
            }
        }
        assertThat(reports).isGreaterThan(100);
        // guard against the comparison passing vacuously: every scenario but the constant-rate busy one evicts
        assertThat(evicting).as("reports where tritium had evicted something").isGreaterThan(0);
    }

    static Stream<Arguments> fullScenarios() {
        List<Arguments> scenarios = List.of(
                Arguments.of("1.5/s, bimodal", Duration.ofHours(3), (Supplier<Workload>) () -> steady(1.5, bimodal())),
                Arguments.of("20,000 slow calls, then a fast trickle", Duration.ofHours(3), (Supplier<Workload>)
                        () -> bigBurstThenTrickle()),
                Arguments.of("10 minutes at 2.5/s every 30 minutes", Duration.ofHours(4), (Supplier<Workload>)
                        () -> frequentBursts()));
        return Stream.of(Kind.values())
                .flatMap(kind -> scenarios.stream()
                        .map(scenario -> Arguments.of(kind, scenario.get()[0], scenario.get()[1], scenario.get()[2])));
    }

    /**
     * Once reservoirs fill, which samples they keep is random, so they can't be compared sample for sample, and
     * eviction also changes which later updates get in. Instead both are scored against the exact decayed
     * distribution of every update: at each report, how far each quantile's value is in rank from where it should be.
     * Eviction must be about as accurate as dropwizard's reservoir.
     */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("fullScenarios")
    void fullReservoirsAreAsAccurateAsDropwizard(
            Kind kind, String _name, Duration duration, Supplier<Workload> workloads) {
        double[] dropwizardError = new double[QUANTILES.length];
        double[] tritiumError = new double[QUANTILES.length];
        int reports = 0;
        int evicting = 0;
        for (int seed = 0; seed < 10; seed++) {
            Workload workload = workloads.get();
            ManualClock clock = new ManualClock();
            Reservoir dropwizard = com.codahale.metrics.LockFreeExponentiallyDecayingReservoir.builder()
                    .clock(clock)
                    .build();
            Reservoir tritium = kind.createDefault(clock);
            ExactDistribution exact = new ExactDistribution();
            SplittableRandom random = new SplittableRandom(seed);
            long end = duration.toNanos();
            long nextUpdate = workload.next(0, random);
            for (long report = REPORT_INTERVAL; report <= end; report += REPORT_INTERVAL) {
                while (nextUpdate <= report) {
                    clock.nanos = nextUpdate;
                    long value = workload.value(nextUpdate, random);
                    dropwizard.update(value);
                    tritium.update(value);
                    exact.update(nextUpdate, value);
                    nextUpdate = workload.next(nextUpdate, random);
                }
                clock.nanos = report;
                Snapshot expected = dropwizard.getSnapshot();
                Snapshot actual = tritium.getSnapshot();
                if (expected.size() == 0) {
                    continue;
                }
                exact.at(report);
                for (int q = 0; q < QUANTILES.length; q++) {
                    dropwizardError[q] += exact.rankError(QUANTILES[q], expected.getValue(QUANTILES[q]));
                    tritiumError[q] += exact.rankError(QUANTILES[q], actual.getValue(QUANTILES[q]));
                }
                reports++;
                if (actual.size() < expected.size()) {
                    evicting++;
                }
            }
        }
        assertThat(evicting).as("reports where tritium had evicted something").isGreaterThan(reports / 10);
        for (int q = 0; q < QUANTILES.length; q++) {
            // sampling noise between runs is a few percent; this catches eviction biasing what full reservoirs keep
            assertThat(tritiumError[q] / reports)
                    .as("q%s mean rank error, dropwizard's is %s", QUANTILES[q], dropwizardError[q] / reports)
                    .isLessThanOrEqualTo(1.25 * dropwizardError[q] / reports + 0.0005);
        }
    }

    /** Every update, weighted by its decay as of a report, ignoring updates too old to matter. */
    private static final class ExactDistribution {
        private static final double ALPHA_PER_NANO = 0.015 / 1e9;

        private final List<long[]> updates = new java.util.ArrayList<>();
        private int first;
        private double[] weights = new double[0];
        private double total;

        void update(long nanos, long value) {
            updates.add(new long[] {nanos, value});
        }

        void at(long nanos) {
            // weights below 1e-15 of the newest change no rank by more than 1e-15 per update
            while (first < updates.size() && (nanos - updates.get(first)[0]) * ALPHA_PER_NANO > 35) {
                first++;
            }
            weights = new double[updates.size() - first];
            total = 0;
            for (int i = first; i < updates.size(); i++) {
                weights[i - first] = Math.exp(-(nanos - updates.get(i)[0]) * ALPHA_PER_NANO);
                total += weights[i - first];
            }
        }

        /** How far {@code quantile} is from the ranks the exact distribution gives {@code value}. */
        double rankError(double quantile, double value) {
            double below = 0;
            double atOrBelow = 0;
            for (int i = first; i < updates.size(); i++) {
                long sample = updates.get(i)[1];
                if (sample < value) {
                    below += weights[i - first];
                }
                if (sample <= value) {
                    atOrBelow += weights[i - first];
                }
            }
            return Math.max(0, Math.max(below / total - quantile, quantile - atOrBelow / total));
        }
    }

    private static void assertWithinBound(Snapshot actual, Snapshot expected, long nanos) {
        String at = TimeUnit.NANOSECONDS.toSeconds(nanos) + " s";
        assertThat(actual.size()).as(at).isPositive().isLessThanOrEqualTo(expected.size());
        assertThat(actual.getMax()).as(at).isLessThanOrEqualTo(expected.getMax());
        assertThat(actual.getMin()).as(at).isGreaterThanOrEqualTo(expected.getMin());
        for (double quantile : QUANTILES) {
            assertThat(actual.getValue(quantile))
                    .as("%s q%s", at, quantile)
                    .isBetween(
                            expected.getValue(Math.max(0, quantile - RANK_SLACK)),
                            expected.getValue(Math.min(1, quantile + RANK_SLACK)));
        }
        double range = expected.getMax() - expected.getMin();
        double rounding = 1e-9 * Math.abs(expected.getMean()) + 1e-9;
        assertThat(Math.abs(actual.getMean() - expected.getMean()))
                .as("%s mean", at)
                .isLessThanOrEqualTo(RANK_SLACK * range + rounding);
        assertThat(Math.abs(actual.getStdDev() - expected.getStdDev()))
                .as("%s stddev", at)
                .isLessThanOrEqualTo(Math.sqrt(1.5 * RANK_SLACK) * range + rounding);
    }

    private interface Values {
        long sample(long nanos, SplittableRandom random);
    }

    private static Workload steady(double perSecond, Values values) {
        return workload(_t -> perSecond, perSecond, values);
    }

    /** Poisson arrivals at {@code rate(t)} per second, by thinning candidates at {@code maxRate}. */
    private static Workload workload(Function<Long, Double> rate, double maxRate, Values values) {
        return new Workload() {
            @Override
            public long next(long afterNanos, SplittableRandom random) {
                long candidate = afterNanos;
                while (candidate < TimeUnit.DAYS.toNanos(1)) {
                    candidate += Math.max(1, (long) (-Math.log(1 - random.nextDouble()) / maxRate * 1e9));
                    if (random.nextDouble() * maxRate <= rate.apply(candidate)) {
                        return candidate;
                    }
                }
                return Long.MAX_VALUE;
            }

            @Override
            public long value(long nanos, SplittableRandom random) {
                return values.sample(nanos, random);
            }
        };
    }

    private static Values lognormal(double medianMillis, double sigma) {
        return (_t, random) -> (long) (medianMillis * 1e6 * Math.exp(sigma * gaussian(random)));
    }

    private static Values outliers() {
        Values normal = lognormal(2, 0.8);
        return (t, random) -> normal.sample(t, random) * (random.nextInt(1000) == 0 ? 100 : 1);
    }

    private static Values bimodal() {
        Values fast = lognormal(1, 0.3);
        Values slow = lognormal(500, 0.3);
        return (t, random) -> (random.nextInt(100) == 0 ? slow : fast).sample(t, random);
    }

    /** 1,027 two-second calls at once, quiet for about 15 minutes, then 1 ms calls arriving faster and faster. */
    private static Workload burstThenTrickle() {
        long burstEnd = TimeUnit.SECONDS.toNanos(1);
        long quietEnd = TimeUnit.SECONDS.toNanos(922);
        return new Workload() {
            private int burst;

            @Override
            public long next(long afterNanos, SplittableRandom _random) {
                if (burst < 1027) {
                    burst++;
                    return afterNanos + burstEnd / 1027;
                }
                if (afterNanos < quietEnd) {
                    return quietEnd;
                }
                // one call every 15 minutes, speeding up to one a second over the hours that follow
                double hours = (afterNanos - quietEnd) / 3.6e12;
                return afterNanos + (long) (TimeUnit.MINUTES.toNanos(15) / Math.pow(900, Math.min(1, hours / 6)));
            }

            @Override
            public long value(long nanos, SplittableRandom _random) {
                return nanos < quietEnd ? TimeUnit.SECONDS.toNanos(2) : TimeUnit.MILLISECONDS.toNanos(1);
            }
        };
    }

    /** 20,000 two-second calls in 10 s, then 1 ms calls at 0.5/s: the burst fills the reservoir, then decays out. */
    private static Workload bigBurstThenTrickle() {
        long burstEnd = TimeUnit.SECONDS.toNanos(10);
        return workload(
                t -> t < burstEnd ? 2000.0 : 0.5,
                2000,
                (t, random) -> t < burstEnd
                        ? TimeUnit.SECONDS.toNanos(2)
                        : TimeUnit.MILLISECONDS.toNanos(1) + random.nextInt(1000));
    }

    /**
     * 10 minutes at 2.5/s every 30 minutes, the last 20 s of each burst 50x slower: each burst fills the reservoir,
     * and the next one starts while the last one's samples are still there but negligible.
     */
    private static Workload frequentBursts() {
        long period = TimeUnit.MINUTES.toNanos(30);
        long length = TimeUnit.MINUTES.toNanos(10);
        Values values = lognormal(30, 0.5);
        return workload(t -> t % period < length ? 2.5 : 0.0, 2.5, (t, random) -> {
            long value = values.sample(t, random);
            return t % period > length - TimeUnit.SECONDS.toNanos(20) ? value * 50 : value;
        });
    }

    /** 10 minutes at 2/s every 2 hours, with the slowest calls at the end of each burst. */
    private static Workload bursts() {
        long period = TimeUnit.HOURS.toNanos(2);
        long length = TimeUnit.MINUTES.toNanos(10);
        Values values = lognormal(30, 0.5);
        return workload(t -> t % period < length ? 2.0 : 0.0, 2, (t, random) -> {
            long value = values.sample(t, random);
            return t % period > length - TimeUnit.SECONDS.toNanos(20) ? value * 50 : value;
        });
    }

    /** 2 calls a second fading to one every 15 minutes over 4 hours, while getting slower. */
    private static Workload goingQuiet() {
        Values values = lognormal(10, 0.5);
        return workload(t -> 2 * Math.exp(-t / 1.8e12) + 0.001, 2.001, (t, random) ->
                (long) (values.sample(t, random) * (1 + t / 3.6e11)));
    }

    private static double gaussian(SplittableRandom random) {
        // Box-Muller; SplittableRandom has no nextGaussian on older JDKs
        return Math.sqrt(-2 * Math.log(1 - random.nextDouble())) * Math.cos(2 * Math.PI * random.nextDouble());
    }

    private static final class ManualClock extends Clock {
        private long nanos;

        @Override
        public long getTick() {
            return nanos;
        }

        @Override
        public long getTime() {
            return TimeUnit.NANOSECONDS.toMillis(nanos);
        }
    }
}
