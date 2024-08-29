/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link LockFreeExponentiallyDecayingReservoirWithExemplarsTest} is based closely on the codahale
 * <a href="https://github.com/palantir/tritium/blob/develop/tritium-registry/src/test/java/com/palantir/tritium/metrics/registry/LockFreeExponentiallyDecayingReservoirTest.java">
 * LockFreeExponentiallyDecayingReservoirTest.java</a>.
 * <p>
 * See {@code LockFreeExponentiallyDecayingReservoirTest.java} Licensed under the Apache License, Version 2.0. You may
 * obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
class LockFreeExponentiallyDecayingReservoirWithExemplarsTest {
    /* Tests for original functionality, also available in LockFreeExponentiallyDecayingReservoir */

    @Test
    public void aReservoirOf100OutOf1000Elements() {
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(100)
                .alpha(0.99)
                .rescaleThreshold(Duration.ofHours(1))
                .build();
        for (int i = 0; i < 1000; i++) {
            reservoir.update(i);
        }

        assertThat(reservoir.size()).isEqualTo(100);

        Snapshot snapshot = reservoir.getSnapshot();

        assertThat(snapshot.size()).isEqualTo(100);

        assertAllValuesBetween(reservoir, 0, 1000);
    }

    @Test
    public void aReservoirOf100OutOf10Elements() {
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(100)
                .alpha(0.99)
                .rescaleThreshold(Duration.ofHours(1))
                .build();
        for (int i = 0; i < 10; i++) {
            reservoir.update(i);
        }

        Snapshot snapshot = reservoir.getSnapshot();

        assertThat(snapshot.size()).isEqualTo(10);

        assertThat(snapshot.size()).isEqualTo(10);

        assertAllValuesBetween(reservoir, 0, 10);
    }

    @Test
    public void aHeavilyBiasedReservoirOf100OutOf1000Elements() {
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(1000)
                .alpha(0.01)
                .build();
        for (int i = 0; i < 100; i++) {
            reservoir.update(i);
        }

        assertThat(reservoir.size()).isEqualTo(100);

        Snapshot snapshot = reservoir.getSnapshot();

        assertThat(snapshot.size()).isEqualTo(100);

        assertAllValuesBetween(reservoir, 0, 100);
    }

    @Test
    public void longPeriodsOfInactivityShouldNotCorruptSamplingState() {
        ManualClock clock = new ManualClock();
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(10)
                .alpha(.15)
                .clock(clock)
                .build();

        // add 1000 values at a rate of 10 values/second
        for (int i = 0; i < 1000; i++) {
            reservoir.update(1000 + i);
            clock.addMillis(100);
        }
        assertThat(reservoir.getSnapshot().size()).isEqualTo(10);
        assertAllValuesBetween(reservoir, 1000, 2000);

        // wait for 15 hours and add another value.
        // this should trigger a rescale. Note that the number of samples will be reduced to 1
        // because scaling factor equal to zero will remove all existing entries after rescale.
        clock.addHours(15);
        reservoir.update(2000);
        assertThat(reservoir.getSnapshot().size()).isEqualTo(1);
        assertAllValuesBetween(reservoir, 1000, 2001);

        // add 1000 values at a rate of 10 values/second
        for (int i = 0; i < 1000; i++) {
            reservoir.update(3000 + i);
            clock.addMillis(100);
        }
        assertThat(reservoir.getSnapshot().size()).isEqualTo(10);
        assertAllValuesBetween(reservoir, 3000, 4000);
    }

    @Test
    public void longPeriodsOfInactivity_fetchShouldResample() {
        ManualClock clock = new ManualClock();
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(10)
                .alpha(.015)
                .clock(clock)
                .build();

        // add 1000 values at a rate of 10 values/second
        for (int i = 0; i < 1000; i++) {
            reservoir.update(1000 + i);
            clock.addMillis(100);
        }
        assertThat(reservoir.getSnapshot().size()).isEqualTo(10);
        assertAllValuesBetween(reservoir, 1000, 2000);

        // wait for 20 hours and take snapshot.
        // this should trigger a rescale. Note that the number of samples will be reduced to 0
        // because scaling factor equal to zero will remove all existing entries after rescale.
        clock.addHours(20);
        Snapshot snapshot = reservoir.getSnapshot();
        assertThat(snapshot.getMax()).isEqualTo(0);
        assertThat(snapshot.getMean()).isEqualTo(0);
        assertThat(snapshot.getMedian()).isEqualTo(0);
        assertThat(snapshot.size()).isEqualTo(0);
    }

    @Test
    public void emptyReservoirSnapshot_shouldReturnZeroForAllValues() {
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(100)
                .alpha(0.015)
                .build();

        Snapshot snapshot = reservoir.getSnapshot();
        assertThat(snapshot.getMax()).isEqualTo(0);
        assertThat(snapshot.getMean()).isEqualTo(0);
        assertThat(snapshot.getMedian()).isEqualTo(0);
        assertThat(snapshot.size()).isEqualTo(0);
    }

    @Test
    public void removeZeroWeightsInSamplesToPreventNaNInMeanValues() {
        ManualClock clock = new ManualClock();
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(1028)
                .alpha(.015)
                .clock(clock)
                .build();
        Timer timer = new Timer(reservoir, clock);

        Context context = timer.time();
        clock.addMillis(100);
        context.stop();

        for (int i = 1; i < 48; i++) {
            clock.addHours(1);
            assertThat(reservoir.getSnapshot().getMean()).isBetween(0.0, Double.MAX_VALUE);
        }
    }

    @Test
    public void multipleUpdatesAfterlongPeriodsOfInactivityShouldNotCorruptSamplingState() throws Exception {
        // This test illustrates the potential race condition in rescale that
        // can lead to a corrupt state.  Note that while this test uses updates
        // exclusively to trigger the race condition, two concurrent updates
        // may be made much more likely to trigger this behavior if executed
        // while another thread is constructing a snapshot of the reservoir;
        // that thread then holds the read lock when the two competing updates
        // are executed and the race condition's window is substantially
        // expanded.

        // Run the test several times.
        for (int attempt = 0; attempt < 10; attempt++) {
            ManualClock clock = new ManualClock();
            Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                    .size(10)
                    .alpha(.015)
                    .clock(clock)
                    .build();

            // Various atomics used to communicate between this thread and the
            // thread created below.
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicInteger threadUpdates = new AtomicInteger(0);
            AtomicInteger testUpdates = new AtomicInteger(0);

            Thread thread = new Thread(() -> {
                int previous = 0;
                while (running.get()) {
                    // Wait for the test thread to update it's counter
                    // before updaing the reservoir.
                    while (true) {
                        int next = testUpdates.get();
                        if (previous < next) {
                            previous = next;
                            break;
                        }
                    }

                    // Update the reservoir.  This needs to occur at the
                    // same time as the test thread's update.
                    reservoir.update(1000);

                    // Signal the main thread; allows the next update
                    // attempt to begin.
                    threadUpdates.incrementAndGet();
                }
            });

            thread.start();

            int sum = 0;
            int previous = -1;
            for (int i = 0; i < 100; i++) {
                // Wait for 15 hours before attempting the next concurrent
                // update.  The delay here needs to be sufficiently long to
                // overflow if an update attempt is allowed to add a value to
                // the reservoir without rescaling.  Note that:
                // e(alpha*(15*60*60)) =~ 10^351 >> Double.MAX_VALUE =~ 1.8*10^308.
                clock.addHours(15);

                // Signal the other thread; asynchronously updates the reservoir.
                testUpdates.incrementAndGet();

                // Delay a variable length of time.  Without a delay here this
                // thread is heavily favored and the race condition is almost
                // never observed.
                for (int j = 0; j < i; j++) {
                    sum += j;
                }

                // Competing reservoir update.
                reservoir.update(1000);

                // Wait for the other thread to finish it's update.
                while (true) {
                    int next = threadUpdates.get();
                    if (previous < next) {
                        previous = next;
                        break;
                    }
                }
            }

            // Terminate the thread.
            running.set(false);
            testUpdates.incrementAndGet();
            thread.join();

            // Test failures will result in normWeights that are not finite;
            // checking the mean value here is sufficient.
            assertThat(reservoir.getSnapshot().getMean()).isBetween(0.0, Double.MAX_VALUE);

            // Check the value of sum; should prevent the JVM from optimizing
            // out the delay loop entirely.
            assertThat(sum).isEqualTo(161700);
        }
    }

    @Test
    public void spotLift() {
        ManualClock clock = new ManualClock();
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(1000)
                .alpha(.015)
                .clock(clock)
                .build();

        int valuesRatePerMinute = 10;
        int valuesIntervalMillis = (int) (TimeUnit.MINUTES.toMillis(1) / valuesRatePerMinute);
        // mode 1: steady regime for 120 minutes
        for (int i = 0; i < 120 * valuesRatePerMinute; i++) {
            reservoir.update(177);
            clock.addMillis(valuesIntervalMillis);
        }

        // switching to mode 2: 10 minutes more with the same rate, but larger value
        for (int i = 0; i < 10 * valuesRatePerMinute; i++) {
            reservoir.update(9999);
            clock.addMillis(valuesIntervalMillis);
        }

        // expect that quantiles should be more about mode 2 after 10 minutes
        assertThat(reservoir.getSnapshot().getMedian()).isEqualTo(9999);
    }

    @Test
    public void spotFall() {
        ManualClock clock = new ManualClock();
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(1000)
                .alpha(.015)
                .clock(clock)
                .build();

        int valuesRatePerMinute = 10;
        int valuesIntervalMillis = (int) (TimeUnit.MINUTES.toMillis(1) / valuesRatePerMinute);
        // mode 1: steady regime for 120 minutes
        for (int i = 0; i < 120 * valuesRatePerMinute; i++) {
            reservoir.update(9998);
            clock.addMillis(valuesIntervalMillis);
        }

        // switching to mode 2: 10 minutes more with the same rate, but smaller value
        for (int i = 0; i < 10 * valuesRatePerMinute; i++) {
            reservoir.update(178);
            clock.addMillis(valuesIntervalMillis);
        }

        // expect that quantiles should be more about mode 2 after 10 minutes
        assertThat(reservoir.getSnapshot().get95thPercentile()).isEqualTo(178);
    }

    @Test
    public void quantiliesShouldBeBasedOnWeights() {
        ManualClock clock = new ManualClock();
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(1000)
                .alpha(.015)
                .clock(clock)
                .build();
        for (int i = 0; i < 40; i++) {
            reservoir.update(177);
        }

        clock.addSeconds(120);

        for (int i = 0; i < 10; i++) {
            reservoir.update(9999);
        }

        assertThat(reservoir.getSnapshot().size()).isEqualTo(50);

        // the first added 40 items (177) have weights 1
        // the next added 10 items (9999) have weights ~6
        // so, it's 40 vs 60 distribution, not 40 vs 10
        assertThat(reservoir.getSnapshot().getMedian()).isEqualTo(9999);
        assertThat(reservoir.getSnapshot().get75thPercentile()).isEqualTo(9999);
    }

    @Test
    public void clockWrapShouldNotRescale() {
        // First verify the test works as expected given low values
        testShortPeriodShouldNotRescale(0);
        // Now revalidate using an edge case nanoTime value just prior to wrapping
        testShortPeriodShouldNotRescale(Long.MAX_VALUE - TimeUnit.MINUTES.toNanos(30));
    }

    /* Tests for exemplar capturing functionality */

    @Test
    public void exemplarValuesAreCapturedFromProvider() {
        final AtomicInteger providerMetadata = new AtomicInteger(0);
        ExemplarMetadataProvider<Integer> provider = providerMetadata::get;
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(100)
                .alpha(0.99)
                .rescaleThreshold(Duration.ofHours(1))
                .exemplarProvider(provider)
                .build();
        for (int i = 0; i < 1000; i++) {
            providerMetadata.set(i);
            reservoir.update(i);
        }

        assertThat(reservoir.getSnapshot()).isInstanceOf(ExemplarsCapture.class);
        ExemplarsCapture exemplarsCapture = (ExemplarsCapture) reservoir.getSnapshot();

        // Verify that returned exemplar values are attributed correctly
        assertThat(exemplarsCapture.getSamples(provider)).hasSize(100);
        exemplarsCapture.getSamples(provider).forEach(exemplar -> {
            assertThat(exemplar.value()).isEqualTo((long) exemplar.metadata());
        });
    }

    @Test
    public void nullExemplarsAreAbsentFromReturnedSnapshot() {
        ExemplarMetadataProvider<Integer> provider = () -> null;
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(100)
                .alpha(0.99)
                .rescaleThreshold(Duration.ofHours(1))
                .exemplarProvider(provider)
                .build();
        for (int i = 0; i < 1000; i++) {
            reservoir.update(i);
        }

        assertThat(reservoir.getSnapshot()).isInstanceOf(ExemplarsCapture.class);
        ExemplarsCapture exemplarsCapture = (ExemplarsCapture) reservoir.getSnapshot();

        // No exemplars should be returned since the provider didn't return non-null metadata
        assertThat(exemplarsCapture.getSamples(provider)).isEmpty();
    }

    private static void testShortPeriodShouldNotRescale(long startTimeNanos) {
        ManualClock clock = new ManualClock(startTimeNanos);
        Reservoir reservoir = LockFreeExponentiallyDecayingReservoirWithExemplars.builder()
                .size(10)
                .alpha(1)
                .clock(clock)
                .build();

        reservoir.update(1000);
        assertThat(reservoir.getSnapshot().size()).isEqualTo(1);

        assertAllValuesBetween(reservoir, 1000, 1001);

        // wait for 10 millis and take snapshot.
        // this should not trigger a rescale. Note that the number of samples will be reduced to 0
        // because scaling factor equal to zero will remove all existing entries after rescale.
        clock.addSeconds(20 * 60);
        Snapshot snapshot = reservoir.getSnapshot();
        assertThat(snapshot.getMax()).isEqualTo(1000);
        assertThat(snapshot.getMean()).isEqualTo(1000);
        assertThat(snapshot.getMedian()).isEqualTo(1000);
        assertThat(snapshot.size()).isEqualTo(1);
    }

    private static void assertAllValuesBetween(Reservoir reservoir, double min, double max) {
        for (double i : reservoir.getSnapshot().getValues()) {
            assertThat(i).isLessThan(max).isGreaterThanOrEqualTo(min);
        }
    }

    private static final class ManualClock extends Clock {
        private final long initialTicksInNanos;
        long ticksInNanos;

        ManualClock(long initialTicksInNanos) {
            this.initialTicksInNanos = initialTicksInNanos;
            this.ticksInNanos = initialTicksInNanos;
        }

        ManualClock() {
            this(0L);
        }

        synchronized void addSeconds(long seconds) {
            ticksInNanos += TimeUnit.SECONDS.toNanos(seconds);
        }

        synchronized void addMillis(long millis) {
            ticksInNanos += TimeUnit.MILLISECONDS.toNanos(millis);
        }

        synchronized void addHours(long hours) {
            ticksInNanos += TimeUnit.HOURS.toNanos(hours);
        }

        @Override
        public synchronized long getTick() {
            return ticksInNanos;
        }

        @Override
        public synchronized long getTime() {
            return TimeUnit.NANOSECONDS.toMillis(ticksInNanos - initialTicksInNanos);
        }
    }
}
