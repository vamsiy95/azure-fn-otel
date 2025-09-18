package com.example;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.metrics.*;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.logs.*;
import io.opentelemetry.sdk.metrics.*;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Function {
    private static final Logger log = LoggerFactory.getLogger(Function.class);
    private static final OpenTelemetry OTEL = initOpenTelemetry();

    private static final Tracer tracer = OTEL.getTracer("com.example.fn", "1.0.0");
    private static final Meter meter = OTEL.getMeter("com.example.fn", "1.0.0");
    private static final LongCounter requests = meter.counterBuilder("demo_requests_total")
            .setDescription("Total requests handled").build();

    private static OpenTelemetry initOpenTelemetry() {
        String endpoint = System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317");

        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(
                        ResourceAttributes.SERVICE_NAME, "azure-fn-otel",
                        ResourceAttributes.SERVICE_VERSION, "1.0.0",
                        ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "local")));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(Sampler.alwaysOn())
                .addSpanProcessor(BatchSpanProcessor.builder(
                        OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build()).build())
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(PeriodicMetricReader.builder(
                        OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).build())
                        .setInterval(java.time.Duration.ofSeconds(10)).build())
                .build();

        SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(
                        OtlpGrpcLogRecordExporter.builder().setEndpoint(endpoint).build()).build())
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .setLoggerProvider(loggerProvider)
                .build();

        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(sdk);
        return sdk;
    }

    @FunctionName("hello")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = { HttpMethod.GET, HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<String> request,
            final ExecutionContext context) {

        requests.add(1);

        Span span = tracer.spanBuilder("hello_handler").startSpan();
        try (Scope s = span.makeCurrent()) {
            log.info("Handling request in hello function");
            String name = request.getQueryParameters().getOrDefault("name", "world");
            span.setAttribute("app.name_param", name);
            log.debug("Computed greeting for name={}", name);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body("Hello, " + name + " 👋")
                    .build();
        } catch (Exception e) {
            span.recordException(e);
            log.error("Error in hello function", e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("oops")
                    .build();
        } finally {
            span.end();
        }
    }
}
