# azure-fn-otel
Java Azure Function with manual OpenTelemetry instrumentation for logs, metrics, and traces

# Azure Function + OpenTelemetry + Grafana LGTM

This is a small demo project that shows how to wire up **an Azure Function (Java)** with **manual OpenTelemetry instrumentation** and ship logs, metrics, and traces into a local **Grafana LGTM stack** (Loki + Grafana + Tempo + Mimir).  
It’s intentionally simple, but it covers the full path end-to-end.

---

## What’s inside

- **Java Azure Function** with a simple HTTP trigger (`/api/hello?name=...`)
- **Manual OpenTelemetry SDK setup** for:
  - Traces (custom spans)
  - Metrics (counter for request count)
  - Logs (via Logback with trace/span IDs in the pattern)
- **Grafana Alloy** configuration to receive OTLP and forward to:
  - **Tempo** (traces)
  - **Mimir** (metrics)
  - **Loki** (logs)
- **Docker Compose stack** to spin up Grafana + the backends locally

---

## Running locally

### 1. Start the observability stack
```bash
cd observability
docker compose up -d
