#!/usr/bin/env python3
"""Run black-box checks against the Coco full-stack sample."""

from __future__ import annotations

import argparse
import json
import os
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from collections import deque
from dataclasses import dataclass
from pathlib import Path
from typing import Any


APPLICATION_OUTPUT_LIMIT = 400
APPLICATION_OUTPUT: dict[int, deque[str]] = {}


@dataclass(frozen=True)
class HttpResult:
    status: int
    headers: dict[str, str]
    body: dict[str, Any]


def main() -> int:
    configure_output_encoding()
    args = parse_args()
    port = free_port()
    base_url = f"http://127.0.0.1:{port}"
    java_command = resolve_java_command(args.java_command)
    process = start_application(args.jar, port, java_command)
    try:
        wait_until_ready(base_url, args.timeout_seconds, process)
        run_business_flow(base_url)
    except Exception:
        output = captured_application_output(process)
        if output:
            log(f"Application output before failure:\n{output}")
        raise
    finally:
        stop_application(process)
    print("Coco full sample business flow verified.")
    return 0


def parse_args() -> argparse.Namespace:
    sample_dir = Path(__file__).resolve().parents[1]
    default_jar = sample_dir / "target" / "coco-sample-full-0.0.1-SNAPSHOT.jar"
    parser = argparse.ArgumentParser(description="Verify the Coco full sample HTTP flow.")
    parser.add_argument("--jar", type=Path, default=default_jar, help="Spring Boot jar to start.")
    parser.add_argument("--java-command", help="Java executable used when starting the sample jar.")
    parser.add_argument("--timeout-seconds", type=int, default=45, help="Application startup timeout.")
    return parser.parse_args()


def configure_output_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            reconfigure(encoding="utf-8", errors="replace")


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.bind(("127.0.0.1", 0))
        return int(server.getsockname()[1])


def resolve_java_command(java_command: str | None) -> str:
    if java_command:
        return java_command
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        executable = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if executable.is_file():
            return str(executable)
    return "java"


def start_application(jar_path: Path, port: int, java_command: str) -> subprocess.Popen[str]:
    if not jar_path.is_file():
        raise AssertionError(f"Sample jar does not exist: {jar_path}")
    command = [java_command, "-jar", str(jar_path), f"--server.port={port}"]
    log("Starting sample jar: " + " ".join(command))
    process = subprocess.Popen(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    APPLICATION_OUTPUT[process.pid] = deque(maxlen=APPLICATION_OUTPUT_LIMIT)
    threading.Thread(target=drain_application_output, args=(process,), daemon=True).start()
    return process


def drain_application_output(process: subprocess.Popen[str]) -> None:
    if process.stdout is None:
        return
    output = APPLICATION_OUTPUT.get(process.pid)
    if output is None:
        return
    for line in process.stdout:
        output.append(line.rstrip())


def captured_application_output(process: subprocess.Popen[str]) -> str:
    output = APPLICATION_OUTPUT.get(process.pid)
    return "" if not output else "\n".join(output)


def stop_application(process: subprocess.Popen[str]) -> None:
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)
    APPLICATION_OUTPUT.pop(process.pid, None)


def wait_until_ready(base_url: str, timeout_seconds: int, process: subprocess.Popen[str]) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        exit_code = process.poll()
        if exit_code is not None:
            raise AssertionError(
                f"Application exited before becoming ready with code {exit_code}.\n"
                f"{captured_application_output(process)}"
            )
        try:
            response = request(base_url, "/full/health", "full-ready")
            if response.status == 200:
                assert_equal(response.body["data"]["status"], "UP", "health status")
                return
        except Exception as exc:
            last_error = exc
        time.sleep(1)
    raise AssertionError(f"Application did not become ready in {timeout_seconds}s: {last_error}")


def run_business_flow(base_url: str) -> None:
    tenant_a = request(
        base_url,
        "/full/orders",
        "full-tenant-a-user-a",
        identity_headers("user-a", "ORDER_READER", "tenant-a"),
    )
    assert_success(tenant_a, "tenant A query")
    assert_visible_order(tenant_a, "A-100", "tenant-a", "user-a")

    tenant_b = request(
        base_url,
        "/full/orders",
        "full-tenant-b-user-a",
        identity_headers("user-a", "ORDER_READER", "tenant-b"),
    )
    assert_success(tenant_b, "tenant B query")
    assert_visible_order(tenant_b, "B-100", "tenant-b", "user-a")

    forbidden = request(
        base_url,
        "/full/orders",
        "full-role-denied",
        identity_headers("user-a", "AUDIT_READER", "tenant-a"),
    )
    assert_error(forbidden, 403, "missing ORDER_READER role")

    missing_tenant = request(
        base_url,
        "/full/orders",
        "full-tenant-missing",
        identity_headers("user-a", "ORDER_READER", None),
    )
    assert_error(missing_tenant, 400, "missing tenant context")

    audits = request(
        base_url,
        "/full/audits",
        "full-audit-reader",
        identity_headers("auditor", "AUDIT_READER", "tenant-a"),
    )
    assert_success(audits, "audit query")
    events = audits.body["data"]
    assert_equal(len(events), 2, "explicit audit event count")
    assert_equal(events[0]["actor"], "user-a", "first audit actor")
    assert_equal(events[0]["tenantId"], "tenant-a", "first audit tenant")
    assert_equal(events[0]["attributes"]["resultCount"], 1, "first audit result count")
    assert_equal(events[1]["tenantId"], "tenant-b", "second audit tenant")
    log("security, tenant SQL, data-permission SQL, and audit checks passed")


def identity_headers(principal_id: str, roles: str, tenant_id: str | None) -> dict[str, str]:
    headers = {
        "X-Coco-Principal-Id": principal_id,
        "X-Coco-Principal-Name": principal_id,
        "X-Coco-Roles": roles,
    }
    if tenant_id is not None:
        headers["X-Coco-Tenant-Id"] = tenant_id
    return headers


def request(base_url: str, path: str, trace_id: str, headers: dict[str, str] | None = None) -> HttpResult:
    req = urllib.request.Request(base_url + path, method="GET")
    req.add_header("Accept", "application/json")
    req.add_header("Accept-Language", "en-US")
    req.add_header("X-Trace-Id", trace_id)
    for name, value in (headers or {}).items():
        req.add_header(name, value)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return parse_response(response.status, dict(response.headers), response.read())
    except urllib.error.HTTPError as exc:
        return parse_response(exc.code, dict(exc.headers), exc.read())


def parse_response(status: int, headers: dict[str, str], raw_body: bytes) -> HttpResult:
    return HttpResult(status, headers, json.loads(raw_body.decode("utf-8")))


def assert_success(response: HttpResult, label: str) -> None:
    assert_equal(response.status, 200, f"{label} HTTP status")
    assert_equal(response.body["success"], True, f"{label} success flag")


def assert_error(response: HttpResult, status: int, label: str) -> None:
    assert_equal(response.status, status, f"{label} HTTP status")
    assert_equal(response.body["success"], False, f"{label} success flag")


def assert_visible_order(response: HttpResult, order_no: str, tenant_id: str, owner_id: str) -> None:
    orders = response.body["data"]
    assert_equal(len(orders), 1, f"{tenant_id}/{owner_id} visible order count")
    assert_equal(orders[0]["orderNo"], order_no, "visible order number")
    assert_equal(orders[0]["tenantId"], tenant_id, "visible order tenant")
    assert_equal(orders[0]["ownerId"], owner_id, "visible order owner")


def assert_equal(actual: Any, expected: Any, label: str) -> None:
    if actual != expected:
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")


def log(message: str) -> None:
    print(f"[coco-full-sample] {message}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
