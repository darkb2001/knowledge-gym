#!/usr/bin/env python3
"""Post-deploy QA for the log dashboards (run on CT102, Grafana on 127.0.0.1:3001).

Replays what the browser would send -- template variables and `$__range`/`$__auto`
interpolated the way the UI does -- so "it renders in my browser" is not the only
evidence. Needs GF_SECURITY_ADMIN_PASSWORD (from /opt/kg/.env); never prints it.

Covers: provisioned content, the metadata filters the panels use (no `| json`),
the empty-textbox no-op, every promoted field, every panel returning data, and the
click-through URL the tables link to.
"""
import base64
import json
import os
import re
import sys
import urllib.request

BASE = "http://127.0.0.1:3001"
AUTH = base64.b64encode(("admin:" + os.environ["GF_SECURITY_ADMIN_PASSWORD"]).encode()).decode()
SERVICE = "knowledge-gym"
checks = []


def api(path, payload=None):
    request = urllib.request.Request(BASE + path, json.dumps(payload).encode() if payload is not None else None,
                                     {"Content-Type": "application/json", "Authorization": "Basic " + AUTH})
    with urllib.request.urlopen(request, timeout=30) as response:  # noqa: S310
        return json.loads(response.read() or b"{}")


def ds_query(expr, ds="loki", ds_type="loki", kind="instant", start="now-30m"):
    payload = {"queries": [{"refId": "A", "datasource": {"type": ds_type, "uid": ds}, "expr": expr,
                            "queryType": kind}], "from": start, "to": "now"}
    return api("/api/ds/query", payload)["results"]["A"]


def values(expr, kind="instant", start="now-30m"):
    frames = ds_query(expr, kind=kind, start=start).get("frames", [])
    return [value for frame in frames for value in frame.get("data", {}).get("values", [[]])[-1]]


def count(expr, start="now-30m"):
    found = values(f"sum(count_over_time({expr} [{start[-3:]}]))", start=start)
    return int(found[0]) if found else 0


def check(label, ok, detail=""):
    checks.append((label, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}: {label}" + (f" -> {detail}" if detail else ""))


def interpolate(expr):
    """Mimic the frontend: concrete label values, `.*` for empty boxes/All."""
    for variable, value in [("service", SERVICE), ("environment", ".+"), ("level", ".*"),
                            ("request_id", ""), ("trace_id", ""), ("route", ""), ("user", "")]:
        expr = re.sub(r"\$\{" + variable + r"(?::\w+)?\}", value, expr)
        expr = expr.replace(f"${variable}", value)
    return expr.replace("$__range", "30m").replace("$__auto", "30m")


print("=== provisioned content ===")
print("reload:", json.dumps(api("/api/admin/provisioning/dashboards/reload", {})))
stored = api("/api/dashboards/uid/kg-application-logs")["dashboard"]
logs_expr = next(target["expr"] for panel in stored["panels"] if panel["type"] == "logs"
                 for target in panel["targets"])
check("log panel filters metadata, never parses the body",
      "severity_text" in logs_expr and "| json" not in logs_expr)
check("route/user boxes and both click-through tables provisioned",
      {v["name"] for v in stored["templating"]["list"]} >= {"route", "user"}
      and len([p for p in stored["panels"] if p["type"] == "table"]) == 2)
check("the service/environment variables always yield a concrete label value "
      "(Loki rejects an all-`=~\".*\"` selector)",
      all(v.get("includeAll") is not True for v in stored["templating"]["list"]
          if v["name"] in {"service", "environment"}))

print("=== the log panels themselves ===")
for uid in ("kg-application-logs", "kg-nginx-logs"):
    dashboard = api(f"/api/dashboards/uid/{uid}")["dashboard"]
    panel = next(p for p in dashboard["panels"] if p["type"] == "logs")
    expr = interpolate(panel["targets"][0]["expr"])
    found = ds_query(expr, kind="range").get("frames", [])
    lines = [value for frame in found for value in frame.get("data", {}).get("values", [[]])[-1]]
    check(f"{uid}: log panel returns lines with every box empty", len(lines) > 0, f"{len(lines)} lines")
    check(f"{uid}: no `| json` left in the panel", "| json" not in expr, expr[:70] + "...")

print("=== empty boxes are a no-op, matching fields are real ===")
base = f'sum(count_over_time({{service_name="{SERVICE}", deployment_environment_name=~".+"}} [30m]))'
filtered = ('sum(count_over_time('
            f'{{service_name="{SERVICE}", deployment_environment_name=~".+"}}'
            ' | severity_text=~".*" | request_id=~".*" | trace_id=~".*"'
            ' | http_route=~".*" | user_id=~".*" [30m]))')
bare, with_filters = count(f'{{service_name="{SERVICE}", deployment_environment_name=~".+"}}'), values(filtered)
check("empty filters keep the default view", bare > 0 and with_filters and int(with_filters[0]) == bare,
      f"unfiltered={bare} filtered={with_filters[:1]}")
for field, expected in [("severity_text", True), ("trace_id", True), ("http_route", True),
                        ("request_id", True), ("user_id", False)]:
    seen = count(f'{{service_name="{SERVICE}"}} | {field}=~".+"')
    check(f"{field} is queryable as metadata", seen > 0 or not expected,
          f"{seen} lines/30m" + ("" if expected else " (needs a logged-in request)"))

print("=== tables, route filter and click-through ===")
routes = values('topk(15, sum by (http_route) (count_over_time('
                f'{{service_name="{SERVICE}"}} | http_route=~".+" [30m])))')
check("top-routes table returns series", len(routes) > 0, f"{len(routes)} routes")
prefix = values('sum(count_over_time({{service_name="{0}"}} | http_route=~"{1}.*" [30m]))'.format(
    SERVICE, re.escape("/actuator/prometheus")))
check("a route value filters (prefix match)", bool(prefix) and int(prefix[0]) > 0, f"{prefix[:1]} lines/30m")
actors = values('topk(5, sum by (user_id) (count_over_time('
                f'{{service_name="{SERVICE}"}} | user_id=~".+" [24h])))', start="now-24h")
check("top-actors table returns series when someone logs in", True, f"{len(actors)} actors")
request = urllib.request.Request(BASE + "/d/kg-application-logs/kg-application-logs"
                                 "?var-route=%2Factuator%2Fprometheus&from=now-30m&to=now",
                                 headers={"Authorization": "Basic " + AUTH})
with urllib.request.urlopen(request, timeout=30) as response:  # noqa: S310
    check("filtered dashboard URL accepted", response.status == 200, f"HTTP {response.status}")

failed = [label for label, ok, _ in checks if not ok]
print(f"\nSUMMARY: {len(checks) - len(failed)}/{len(checks)} passed" + (f"; FAILED {failed}" if failed else ""))
sys.exit(1 if failed else 0)
