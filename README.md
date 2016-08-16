[![Circle CI](https://circle.palantir.build/gh/elements/tritium.svg?style=svg)](https://circle.palantir.build/gh/elements/tritium)
[![Artifactory Release](https://shields.palantir.build/artifactory/internal-jar-release/release/com.palantir.tritium/tritium-lib/svg)](https://artifactory.palantir.build/artifactory/webapp/#/artifacts/browse/tree/General/internal-jar-release/com/palantir/tritium/tritium-lib)
[![Artifactory Snapshot](https://shields.palantir.build/artifactory/internal-jar-snapshot/snapshot/com.palantir.tritium/tritium-lib/svg)](https://artifactory.palantir.build/artifactory/webapp/#/artifacts/browse/tree/General/internal-jar-snapshot/com/palantir/tritium/tritium-lib)

# Tritium

Tritium is a library for instrumenting applications  to provide better
observability at runtime. Tritium allows for instrumentation of service
interfaces through a Java proxy, providing call backs to extensible invocation
event handlers. Two main invocation handlers are currently provided:

* Metrics - records aggregate service time and call rates using Dropwizard metrics
* Logging - logs individual service times

## Basic Usage

### Instrumenting a service interface

```java

import com.palantir.tritium.Tritium;

Service interestingService = ...
Service instrumentedService = Tritium.instrument(Service.class,
        interestingService, environment.metrics());
```


## Creating a metric registry with reservoirs backed by [HDR Histograms](http://hdrhistogram.org/)

### Dropwizard 0.9+ Integration

```java

    @Override
    public void initialize(Bootstrap<ApplicationConfiguration> bootstrap) {
        super.initialize(bootstrap);
        bootstrap.setMetricRegistry(MetricRegistries.createWithHdrHistogramReservoirs());
        ...
    }
```

### Dropwizard 0.8.x Integration

TODO - This requires some hacks through reflection to override the default Dropwizard `MetricRegistry`

