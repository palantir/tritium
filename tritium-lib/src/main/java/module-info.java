module com.palantir.tritium.lib {
    requires preconditions;
    requires com.google.common;
    requires safe.logging;
    requires com.palantir.tritium.api;
    requires com.palantir.tritium.core;
    requires net.bytebuddy;
    requires org.checkerframework.checker.qual;
    requires org.slf4j;

    exports com.palantir.tritium;
}
