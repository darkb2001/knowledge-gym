# Static BusyBox supplies an actual HTTP health probe in otherwise shell-less images.
FROM busybox:1.37.0-musl AS probe
FROM otel/opentelemetry-collector-contrib:0.162.0 AS collector
COPY --from=probe /bin/busybox /bin/busybox
FROM grafana/loki:3.7.8 AS loki
COPY --from=probe /bin/busybox /bin/busybox
FROM grafana/tempo:2.9.0 AS tempo
COPY --from=probe /bin/busybox /bin/busybox
