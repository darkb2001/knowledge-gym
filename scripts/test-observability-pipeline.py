#!/usr/bin/env python3
"""Local-only Collector canary. Requires PyYAML and --collector binary.
Uses ephemeral loopback ports/temp Docker-log fixtures, never Docker/production.
Exercises the checked-in processors/receiver against JSON OTLP test exporters.
"""
import argparse
import contextlib
import copy
import datetime
import gzip
import http.server
import json
import os
import queue
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def get(url):
    if not url.startswith("http://127.0.0.1:"):
        raise ValueError("canary accepts only ephemeral loopback HTTP")
    with urllib.request.urlopen(url, timeout=2) as response:  # noqa: S310
        return response.read()


def wait_until(predicate, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            value = predicate()
            if value:
                return value
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(0.1)
    raise AssertionError("timed out waiting for local canary")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--collector", required=True)
    parser.add_argument("--artifacts", type=Path)
    args = parser.parse_args()
    deliveries = queue.Queue()
    outage = threading.Event()
    failed_delivery = threading.Event()

    class Sink(http.server.BaseHTTPRequestHandler):
        def do_POST(self):
            data = self.rfile.read(int(self.headers["Content-Length"]))
            if self.headers.get("Content-Encoding") == "gzip":
                data = gzip.decompress(data)
            if outage.is_set():
                failed_delivery.set()
                self.send_response(503)
            else:
                deliveries.put((self.path, json.loads(data)))
                self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b"{}")

        def log_message(self, format, *args):
            pass  # Do not print synthetic payloads, even on failure.

    sink = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Sink)
    worker = threading.Thread(target=sink.serve_forever, daemon=True)
    worker.start()
    try:
        with tempfile.TemporaryDirectory(prefix="kg-telemetry-canary-") as tmp:
            tmp = Path(tmp)
            state = tmp / "state"
            state.mkdir()
            logs = tmp / "docker" / "abc"
            logs.mkdir(parents=True)
            logfile = logs / "abc-json.log"
            logfile.touch()
            config = yaml.safe_load((ROOT / "ops/observability/collector.yml").read_text())
            health, metrics, ingress, grpc = [port() for _ in range(4)]
            config["extensions"]["health_check"]["endpoint"] = f"127.0.0.1:{health}"
            config["extensions"]["file_storage"]["directory"] = str(state)
            config["receivers"]["filelog/docker"]["include"] = [str(logs.parent / "*/*-json.log")]
            config["receivers"]["otlp"]["protocols"]["http"]["endpoint"] = f"127.0.0.1:{ingress}"
            config["receivers"]["otlp"]["protocols"]["grpc"]["endpoint"] = f"127.0.0.1:{grpc}"
            prom = config["service"]["telemetry"]["metrics"]["readers"][0]["pull"]["exporter"]["prometheus"]
            prom.update(host="127.0.0.1", port=metrics)
            endpoint = f"http://127.0.0.1:{sink.server_port}"
            trace_exporter = copy.deepcopy(config["exporters"]["otlphttp/loki"])
            trace_exporter.update(endpoint=endpoint, encoding="json")
            config["exporters"]["otlphttp/loki"].update(endpoint=endpoint, encoding="json")
            del config["exporters"]["otlp/tempo"]
            config["exporters"]["otlphttp/trace-canary"] = trace_exporter
            config["service"]["pipelines"]["traces"]["exporters"] = ["otlphttp/trace-canary"]
            configfile = tmp / "collector.yml"
            configfile.write_text(yaml.safe_dump(config))
            stderr = tmp / "collector.stderr"
            output = stderr.open("wb")
            process = subprocess.Popen([args.collector, "--config", str(configfile)],
                                       stdout=output, stderr=subprocess.STDOUT,
                                       env={**os.environ, "GOMEMLIMIT": "180MiB"})
            try:
                wait_until(lambda: get(f"http://127.0.0.1:{health}/"))
                time.sleep(1.5)  # Receiver start_at=end must discover the empty fixture.
                trace_id, span_id = "1" * 32, "2" * 16
                sentinel = "synthetic-must-not-leave-collector"

                def append(body, service="knowledge-gym"):
                    entry = {"log": json.dumps(body) + "\n", "stream": "stdout",
                             "time": datetime.datetime.now(datetime.UTC).isoformat().replace("+00:00", "Z"),
                             "attrs": {"telemetry.service": service, "telemetry.environment": "canary"}}
                    with logfile.open("a") as handle:
                        handle.write(json.dumps(entry) + "\n")

                append({"level": "INFO", "message": "canary_safe", "trace_id": trace_id,
                        "span_id": span_id, "request_id": "3" * 32, "authorization": sentinel,
                        "cookie": sentinel, "request_body": sentinel})
                append({"level": "warn", "message": sentinel, "stack_trace": sentinel,
                        "logger": sentinel, "http_route": sentinel}, "grafana")
                # Unlabelled/non-JSON input must not be sent or echoed by parser diagnostics.
                with logfile.open("a") as handle:
                    handle.write(json.dumps({"log": sentinel, "time": "invalid"}) + "\n")
                append({"level": "INFO", "message": sentinel}, "unapproved-service")
                now = time.time_ns()
                trace = {"resourceSpans": [{"resource": {"attributes": [
                    {"key": "service.name", "value": {"stringValue": "knowledge-gym"}},
                    {"key": "deployment.environment.name", "value": {"stringValue": "canary"}},
                    {"key": "user.email", "value": {"stringValue": sentinel}}]},
                    "scopeSpans": [{"scope": {"name": "canary"}, "spans": [{
                        "traceId": trace_id, "spanId": span_id, "traceState": "kg=" + sentinel,
                        "name": sentinel, "kind": 2,
                        "startTimeUnixNano": str(now), "endTimeUnixNano": str(now + 1_000_000),
                        "status": {"code": 2, "message": sentinel},
                        "attributes": [
                            {"key": "http.request.method", "value": {"stringValue": "GET"}},
                            {"key": "http.route", "value": {"stringValue": "/canary/{id}"}},
                            *[{"key": key, "value": {"stringValue": sentinel}} for key in
                              ["db.statement", "db.query.text", "url.full", "http.request.body",
                               "messaging.message.body", "authorization", "user.email", "client.address"]]],
                        "events": [{"name": sentinel, "timeUnixNano": str(now), "attributes": [
                            {"key": "exception.message", "value": {"stringValue": sentinel}}]}],
                        "links": []
                    }]}]}]}
                # Linked spans are rejected fail-closed, not passed through unsanitized.
                linked = copy.deepcopy(trace["resourceSpans"][0]["scopeSpans"][0]["spans"][0])
                linked["spanId"] = "6" * 16
                linked["links"] = [{"traceId": "4" * 32, "spanId": "5" * 16, "attributes": [
                    {"key": "private", "value": {"stringValue": sentinel}}]}]
                trace["resourceSpans"][0]["scopeSpans"][0]["spans"].append(linked)

                def send_trace():
                    request = urllib.request.Request(f"http://127.0.0.1:{ingress}/v1/traces",
                        json.dumps(trace).encode(), {"Content-Type": "application/json"})
                    # URL is constructed here from an ephemeral loopback port.
                    try:
                        with urllib.request.urlopen(request, timeout=3) as response:  # noqa: S310
                            assert response.status == 200
                    except urllib.error.HTTPError as error:
                        detail = error.read().decode().replace(sentinel, "[SYNTHETIC]")
                        raise AssertionError(f"OTLP ingress HTTP {error.code}: {detail[:500]}") from None

                send_trace()
                collected = []
                deadline = time.monotonic() + 15
                while time.monotonic() < deadline:
                    with contextlib.suppress(queue.Empty):
                        collected.append(deliveries.get(timeout=1))
                    if any(p == "/v1/logs" for p, _ in collected) and any(p == "/v1/traces" for p, _ in collected):
                        break
                assert any(p == "/v1/logs" for p, _ in collected), "log receiver/export missing"
                assert any(p == "/v1/traces" for p, _ in collected), "trace receiver/export missing"
                assert sentinel not in json.dumps(collected), "redaction failed (payload withheld)"
                logs_payload = next(v for p, v in collected if p == "/v1/logs")
                records = [record for resource in logs_payload["resourceLogs"]
                           for scope in resource["scopeLogs"] for record in scope["logRecords"]]
                assert len(records) == 2, "unapproved/unstructured input was not dropped"
                assert any(r.get("traceId") == trace_id for r in records), "trace ID conversion failed"
                traces_payload = next(v for p, v in collected if p == "/v1/traces")
                spans = [span for resource in traces_payload["resourceSpans"]
                         for scope in resource["scopeSpans"] for span in scope["spans"]]
                assert len(spans) == 1, "linked span must be rejected without rejecting clean span"
                assert not spans[0].get("events"), "span events must be removed"
                print("PASS: Docker JSON selection, schema allowlist, IDs, logs and OTLP traces")
                print("PASS: SQL/URL/body/header/user/event/link redaction; no raw diagnostic echo")

                # Backend 503: ingress stays healthy; bounded queues + retries are observable.
                outage.set()
                send_trace()
                assert failed_delivery.wait(10), "sink must actually return 503"
                text = get(f"http://127.0.0.1:{metrics}/metrics").decode()
                assert "otelcol_exporter_queue_capacity" in text
                assert "otelcol_receiver_accepted_spans" in text
                assert get(f"http://127.0.0.1:{health}/")
                assert process.poll() is None
                print("PASS: backend 503 does not stop Collector ingress; queue metrics present")
                outage.clear()
                recovered_path, recovered_payload = deliveries.get(timeout=20)
                assert recovered_path == "/v1/traces"
                assert sentinel not in json.dumps(recovered_payload)
                print("PASS: retry exports sanitized traces after backend recovery")
                process.terminate()
                process.wait(timeout=15)
                append({"level": "INFO", "message": "restart_safe"})
                process = subprocess.Popen([args.collector, "--config", str(configfile)],
                    stdout=output, stderr=subprocess.STDOUT,
                    env={**os.environ, "GOMEMLIMIT": "180MiB"})
                wait_until(lambda: get(f"http://127.0.0.1:{health}/"))
                resumed_path, resumed_payload = deliveries.get(timeout=15)
                assert resumed_path == "/v1/logs"
                assert "restart_safe" in json.dumps(resumed_payload)
                assert "canary_safe" not in json.dumps(resumed_payload)
                print("PASS: persisted file offsets resume offline log without replaying old records")
            finally:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
                output.close()
                if args.artifacts:
                    args.artifacts.mkdir(parents=True, exist_ok=True)
                    (args.artifacts / "collector.stderr").write_text(stderr.read_text())
            assert "synthetic-must-not-leave-collector" not in stderr.read_text(), "parser diagnostics leaked"
    finally:
        sink.shutdown()
        sink.server_close()


if __name__ == "__main__":
    main()
