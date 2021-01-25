module tritium.test {
    exports com.palantir.tritium.metrics;
    exports com.palantir.tritium.test;
    exports com.palantir.tritium.test.event;

    requires transitive tritium.api;
    requires transitive tritium.core;
}
