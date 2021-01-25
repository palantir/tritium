module com.palantir.tritium.core {
    exports com.palantir.tritium.event;

    requires transitive com.palantir.tritium.api;
    requires com.google.common;
    requires org.checkerframework.checker.qual;
    requires org.slf4j;
    requires preconditions;
    requires safe.logging;
}
