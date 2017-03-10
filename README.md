# [Tritium](https://github.com/palantir/tritium)

Tritium is a library for instrumenting applications  to provide better observability at runtime. Tritium allows for instrumentation of service interfaces through a Java proxy, providing call backs to extensible invocation event handlers. Two main invocation handlers currently provided are:

* Metrics - records aggregate service time and call rates using Dropwizard metrics
* Logging - logs individual service times

## Why Tritium?

Tritium gives us aggregate metrics for the various services exposed and consumed by a server.

* invocation response times (including min, average, max, percentile distribution, request count and 1, 5, and 15 rates)
* cache effectiveness (eviction count, hit count, hit ratio, load average millis, load failure count, load success count, miss count, miss ratio, request count)

These metrics can be exposed at the Dropwizard ``MetricsServlet`` and can be exported via any of the [Dropwizard provided reporters](http://metrics.dropwizard.io/3.1.0/manual/core/#reporters).

## Basic Usage

### Instrumenting a service interface of a dropwizard application with default metrics timers and optional trace logging.

```java
import com.palantir.tritium.Tritium;

Service interestingService = ...
Service instrumentedService = Tritium.instrument(Service.class,
        interestingService, environment.metrics());
```

## Creating a metric registry with reservoirs backed by [HDR Histograms](http://hdrhistogram.org/).

HDR histograms are more useful if the service is long running, so the stats represents the lifetime of the server rather than using default exponential decay which can lead to some mis-interpretations of timings (especially higher percentiles and things like max dropping over time) if the consumer isn't aware of these assumptions.

Note that the Histogram collects metrics throughout the lifetime of the service.

### Dropwizard 0.9+ Integration

```java

    @Override
    public void initialize(Bootstrap<ApplicationConfiguration> bootstrap) {
        super.initialize(bootstrap);
        bootstrap.setMetricRegistry(MetricRegistries.createWithHdrHistogramReservoirs());
        ...
    }
```

License
-------
This project is made available under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).

