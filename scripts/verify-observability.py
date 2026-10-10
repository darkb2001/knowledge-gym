#!/usr/bin/env python3
"""Source-only observability contract + deploy-marker tests. Never deploys.
Requires PyYAML; invokes only Compose config and a fake Docker CLI.
"""
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


def read_yaml(path):
    return yaml.safe_load((ROOT / path).read_text())


def verify_config():
    ops = read_yaml("ops/docker-compose.ops.yml")
    overlay = read_yaml("docker-compose.observability.yml")
    collector = read_yaml("ops/observability/collector.yml")
    loki = read_yaml("ops/observability/loki.yml")
    tempo = read_yaml("ops/observability/tempo.yml")
    base_prom = read_yaml("infra/prometheus.yml")
    prom = read_yaml("infra/prometheus-observability.yml")
    assert ops["networks"]["telemetry"]["internal"] is True
    total = 0
    for name in ["otel-collector", "loki", "tempo"]:
        service = ops["services"][name]
        assert not service.get("ports") and not service.get("privileged")
        assert service["profiles"] == ["observability"]
        assert service["read_only"] and service["cap_drop"] == ["ALL"]
        assert service["healthcheck"]["test"][1:3] == ["/bin/busybox", "wget"]
        assert service["networks"] == ["telemetry"]
        assert "latest" not in service["image"]
        assert service["mem_limit"] == service["memswap_limit"]
        total += int(service["mem_limit"].removesuffix("m"))
    assert total == 1024
    assert "docker.sock" not in json.dumps(ops["services"])
    assert "docker.sock" not in json.dumps(overlay["services"])
    app = overlay["services"]["app"]
    env = app["environment"]
    assert env["OTEL_LOGS_EXPORTER"] == env["OTEL_METRICS_EXPORTER"] == "none"
    assert env["OTEL_PROPAGATORS"] == "tracecontext"
    assert env["OTEL_SPAN_EVENT_COUNT_LIMIT"] == env["OTEL_SPAN_LINK_COUNT_LIMIT"] == "0"
    assert env["OTEL_EXPORTER_OTLP_TIMEOUT"] == "3000"
    assert "-javaagent:/app/otel-javaagent.jar" in env["JAVA_TOOL_OPTIONS"]
    assert app["logging"]["options"]["labels"] == "telemetry.service,telemetry.environment"
    for pipeline in collector["service"]["pipelines"].values():
        assert pipeline["processors"][0] == "memory_limiter"
    assert collector["processors"]["filter/span-safety"]["error_mode"] == "propagate"
    for exporter in collector["exporters"].values():
        assert exporter["timeout"] == "3s"
        assert exporter["sending_queue"]["queue_size"] == 64
        assert exporter["retry_on_failure"]["max_elapsed_time"] == "60s"
    assert "set(events, [])" not in (ROOT / "ops/observability/collector.yml").read_text()
    assert loki["limits_config"]["retention_period"] == "72h"
    indexed = loki["limits_config"]["otlp_config"]["resource_attributes"]
    assert indexed["ignore_defaults"]
    assert indexed["attributes_config"][0]["attributes"] == ["service.name", "deployment.environment.name"]
    assert tempo["compactor"]["compaction"]["block_retention"] == "24h"
    for job in base_prom["scrape_configs"]:
        assert job in prom["scrape_configs"], "existing scrape drift"
    assert base_prom["alerting"] == prom["alerting"], "existing Alertmanager route changed"
    assert set(base_prom["rule_files"]) <= set(prom["rule_files"])
    nginx = (ROOT / "infra/nginx.prod.conf").read_text()
    log_format = nginx.split("log_format kg_json", 1)[1].split(";", 1)[0]
    for forbidden in ["$request_uri", "$args", "$remote_addr", "$http_user_agent", "$request_body", "$uri"]:
        assert forbidden not in log_format
    assert "proxy_set_header X-Request-ID $request_id;" in nginx
    assert "proxy_set_header traceparent $http_traceparent;" in nginx
    assert "error_log /dev/null crit;" in nginx
    datasources = read_yaml("infra/grafana/provisioning/datasources/datasource.yml")["datasources"]
    by_uid = {d["uid"]: d for d in datasources}
    assert set(by_uid) == {"prometheus", "loki", "tempo"}
    derived = by_uid["loki"]["jsonData"]["derivedFields"][0]
    match = re.search(derived["matcherRegex"], json.dumps({"trace_id": "1" * 32}))
    assert match is not None and match.group(1) == "1" * 32
    assert derived["datasourceUid"] == "tempo"
    assert "$${__trace.traceId}" in by_uid["tempo"]["jsonData"]["tracesToLogsV2"]["query"]
    for path in (ROOT / "infra/grafana/dashboards").glob("kg-*.json"):
        dashboard = json.loads(path.read_text())
        assert not dashboard["editable"]
        variables = {v["name"] for v in dashboard["templating"]["list"]}
        for panel in dashboard["panels"]:
            for target in panel.get("targets", []):
                expression = target.get("expr", target.get("query", ""))
                used = re.findall(r"\$\{(\w+)(?::\w+)?\}|\$(\w+)", expression)
                assert {a or b for a, b in used} <= variables
    print("PASS: resource/security/retention/label/sampling/datasource/dashboard contracts")


def verify_deploy_markers():
    # Execute the real deployment script with a fake CLI, not Docker/production.
    with tempfile.TemporaryDirectory(prefix="kg-deploy-contract-") as tmp:
        tmp = Path(tmp)
        (tmp / "run").mkdir()
        (tmp / "docker-compose.prod.yml").touch()
        (tmp / "docker-compose.observability.yml").touch()
        docker = tmp / "docker"
        docker.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$CALLS"\n'
                          'if [ "$1" = network ] && [ "$NETWORK_OK" = no ]; then exit 1; fi\n')
        docker.chmod(0o700)
        for enabled, network_ok in [(False, True), (True, True), (True, False)]:
            calls = tmp / "calls"
            calls.write_text("")
            marker = tmp / "run/observability.enabled"
            if enabled:
                marker.touch()
            elif marker.exists():
                marker.unlink()
            (tmp / "run/elasticsearch.stopped").touch()
            result = subprocess.run(["bash", str(ROOT / "scripts/deploy-prod.sh")],
                env={"PATH": str(tmp) + os.pathsep + os.environ["PATH"], "KG_ROOT": str(tmp),
                     "APP_IMAGE": "synthetic-release", "CALLS": str(calls),
                     "NETWORK_OK": "yes" if network_ok else "no"}, capture_output=True, text=True)
            lines = calls.read_text().splitlines()
            assert (result.returncode == 0) == network_ok
            compose = [line for line in lines if line.startswith("compose ")]
            if not network_ok:
                assert not compose, "missing network must fail before any app deployment"
            else:
                assert len(compose) == 4
                assert any("stop elasticsearch" in line for line in compose)
                for line in compose:
                    assert ("docker-compose.observability.yml" in line) == enabled
                    assert "ops/docker-compose" not in line
        ci = (ROOT / ".github/workflows/ci.yml").read_text()
        assert "run/observability.enabled" in ci
        assert 'compose+=(-f docker-compose.observability.yml)' in ci
    print("PASS: deploy marker on/off, ES stop preservation and missing-network fail-fast (fake CLI)")


def verify_compose():
    # Never load the operator's .env or print resolved credentials.
    required = re.findall(r"\$\{([A-Z0-9_]+):\?", (ROOT / "docker-compose.prod.yml").read_text())
    env = {"PATH": os.environ["PATH"], "HOME": str(Path.home())}
    env.update(dict.fromkeys(required, "synthetic-not-for-deployment"))
    env["KG_DOCS_DIR"] = str(ROOT / "docs")
    commands = [
        ["docker", "compose", "--env-file", "/dev/null", "-p", "kgops", "-f",
         "ops/docker-compose.ops.yml", "--profile", "observability", "config", "--quiet"],
        ["docker", "compose", "--env-file", "/dev/null", "-p", "kg", "-f",
         "docker-compose.prod.yml", "-f", "docker-compose.observability.yml", "config", "--quiet"],
    ]
    for command in commands:
        subprocess.run(command, cwd=ROOT, env=env, check=True)
    print("PASS: both Compose models, source-only with synthetic env and --quiet")


if __name__ == "__main__":
    verify_config()
    verify_deploy_markers()
    verify_compose()
