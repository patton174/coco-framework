#!/usr/bin/env python3
"""Run black-box checks against the Coco sample business application."""

from __future__ import annotations

import argparse
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class HttpResult:
    """HTTP response snapshot used by the black-box checks."""

    status: int
    headers: dict[str, str]
    body: dict[str, Any]


def main() -> int:
    args = parse_args()
    base_url = args.base_url
    process: subprocess.Popen[str] | None = None
    if base_url is None:
        port = free_port()
        base_url = f"http://127.0.0.1:{port}"
        java_command = resolve_java_command(args.java_command)
        log(f"Starting sample jar: {java_command} -jar {args.jar} --server.port={port}")
        process = start_application(args.jar, port, java_command)
        wait_until_ready(base_url, args.timeout_seconds, process)
    try:
        run_business_flow(base_url)
    finally:
        if process is not None:
            stop_application(process)
    print("Coco sample business flow verified.")
    return 0


def parse_args() -> argparse.Namespace:
    sample_dir = Path(__file__).resolve().parents[1]
    default_jar = sample_dir / "target" / "coco-sample-basic-0.0.1-SNAPSHOT.jar"
    parser = argparse.ArgumentParser(description="Verify Coco sample business HTTP flow.")
    parser.add_argument("--base-url", help="Existing sample application base URL.")
    parser.add_argument("--jar", type=Path, default=default_jar, help="Spring Boot jar to start.")
    parser.add_argument("--java-command", help="Java executable used when starting the sample jar.")
    parser.add_argument("--timeout-seconds", type=int, default=45, help="Application startup timeout.")
    return parser.parse_args()


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.bind(("127.0.0.1", 0))
        return int(server.getsockname()[1])


def resolve_java_command(java_command: str | None) -> str:
    if java_command:
        return java_command
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        java_executable = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if java_executable.is_file():
            return str(java_executable)
    return "java"


def start_application(jar_path: Path, port: int, java_command: str) -> subprocess.Popen[str]:
    if not jar_path.is_file():
        raise AssertionError(f"Sample jar does not exist: {jar_path}")
    command = [java_command, "-jar", str(jar_path), f"--server.port={port}"]
    return subprocess.Popen(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def stop_application(process: subprocess.Popen[str]) -> None:
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)


def wait_until_ready(base_url: str, timeout_seconds: int, process: subprocess.Popen[str]) -> None:
    log(f"Waiting for sample application: {base_url}")
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        exit_code = process.poll()
        if exit_code is not None:
            output = process.stdout.read() if process.stdout is not None else ""
            raise AssertionError(f"Application exited before becoming ready with code {exit_code}.\n{output}")
        try:
            response = request("GET", base_url, "/sample/products", trace_id="python-ready")
            if response.status == 200:
                log_response("READY", "GET", "/sample/products", response)
                return
        except Exception as exc:
            last_error = exc
        time.sleep(1)
    raise AssertionError(f"Application did not become ready in {timeout_seconds}s: {last_error}")


def run_business_flow(base_url: str) -> None:
    log(f"Running business flow against {base_url}")

    products = request("GET", base_url, "/sample/products", trace_id="python-products")
    log_response("PRODUCTS", "GET", "/sample/products", products)
    assert_success(products, "python-products", "/sample/products")
    assert_equal(products.body["data"][0]["sku"], "COCO-STARTER", "first product sku")
    assert_equal(products.body["data"][0]["availableStock"], 5, "first product stock")
    log("  first product: sku=COCO-STARTER stock=5")

    created = request_json(
        base_url,
        "/sample/orders",
        {"buyerName": "Patton", "sku": "COCO-STARTER", "quantity": 2},
        trace_id="python-create-order",
    )
    log_response("CREATE_ORDER", "POST", "/sample/orders", created)
    assert_success(created, "python-create-order", "/sample/orders")
    order_id = created.body["data"]["orderId"]
    assert_equal(created.body["data"]["status"], "CREATED", "created order status")
    assert_equal(created.body["data"]["totalAmount"], 19800, "created order total")
    assert_equal(created.body["data"]["remainingStock"], 3, "remaining stock after order")
    log(f"  created order: orderId={order_id} totalAmount=19800 remainingStock=3")

    loaded = request("GET", base_url, f"/sample/orders/{order_id}", trace_id="python-load-order")
    log_response("LOAD_ORDER", "GET", f"/sample/orders/{order_id}", loaded)
    assert_success(loaded, "python-load-order", f"/sample/orders/{order_id}")
    assert_equal(loaded.body["data"]["orderId"], order_id, "loaded order id")
    assert_equal(loaded.body["data"]["buyerName"], "Patton", "loaded buyer")
    log(f"  loaded order: orderId={order_id} buyerName=Patton")

    insufficient = request_json(
        base_url,
        "/sample/orders",
        {"buyerName": "Patton", "sku": "COCO-STARTER", "quantity": 99},
        trace_id="python-stock-error-zh",
        accept_language="zh-CN",
    )
    log_response("INSUFFICIENT_STOCK_ZH", "POST", "/sample/orders", insufficient)
    assert_equal(insufficient.status, 409, "insufficient stock HTTP status")
    assert_equal(insufficient.body["success"], False, "insufficient stock success flag")
    assert_equal(insufficient.body["code"], "sample.order.insufficient-stock", "insufficient stock code")
    assert_equal(
        insufficient.body["message"],
        "\u5546\u54c1 COCO-STARTER \u5e93\u5b58\u4e0d\u8db3\uff0c"
        "\u5f53\u524d\u5e93\u5b58 3\uff0c\u8bf7\u6c42\u6570\u91cf 99",
        "insufficient stock zh-CN message",
    )
    assert_equal(insufficient.body["traceId"], "python-stock-error-zh", "insufficient stock zh-CN trace")
    log("  stock error zh-CN: code=sample.order.insufficient-stock status=409")

    english_insufficient = request_json(
        base_url,
        "/sample/orders",
        {"buyerName": "Patton", "sku": "COCO-STARTER", "quantity": 99},
        trace_id="python-stock-error-en",
        accept_language="en-US",
    )
    log_response("INSUFFICIENT_STOCK_EN", "POST", "/sample/orders", english_insufficient)
    assert_equal(english_insufficient.status, 409, "insufficient stock en-US HTTP status")
    assert_equal(english_insufficient.body["success"], False, "insufficient stock en-US success flag")
    assert_equal(english_insufficient.body["code"], "sample.order.insufficient-stock", "insufficient stock en-US code")
    assert_equal(
        english_insufficient.body["message"],
        "Product COCO-STARTER has insufficient stock, current stock 3, requested quantity 99",
        "insufficient stock en-US message",
    )
    assert_equal(english_insufficient.body["traceId"], "python-stock-error-en", "insufficient stock en-US trace")
    log("  stock error en-US: code=sample.order.insufficient-stock status=409")


def request(
    method: str,
    base_url: str,
    path: str,
    trace_id: str,
    accept_language: str | None = None,
) -> HttpResult:
    req = urllib.request.Request(base_url + path, method=method)
    req.add_header("Accept", "application/json")
    req.add_header("X-Trace-Id", trace_id)
    if accept_language:
        req.add_header("Accept-Language", accept_language)
    return send(req)


def request_json(
    base_url: str,
    path: str,
    payload: dict[str, Any],
    trace_id: str,
    accept_language: str | None = None,
) -> HttpResult:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(base_url + path, method="POST", data=data)
    req.add_header("Accept", "application/json")
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Trace-Id", trace_id)
    if accept_language:
        req.add_header("Accept-Language", accept_language)
    return send(req)


def send(req: urllib.request.Request) -> HttpResult:
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return parse_response(response.status, dict(response.headers), response.read())
    except urllib.error.HTTPError as exc:
        return parse_response(exc.code, dict(exc.headers), exc.read())


def parse_response(status: int, headers: dict[str, str], raw_body: bytes) -> HttpResult:
    body = json.loads(raw_body.decode("utf-8"))
    return HttpResult(status, headers, body)


def assert_success(response: HttpResult, trace_id: str, path: str) -> None:
    assert_equal(response.status, 200, f"{path} HTTP status")
    assert_equal(response.headers.get("X-Trace-Id"), trace_id, f"{path} trace header")
    assert_equal(response.body["success"], True, f"{path} success flag")
    assert_equal(response.body["code"], "coco.success", f"{path} response code")
    assert_equal(response.body["traceId"], trace_id, f"{path} response trace")
    assert_equal(response.body["path"], path, f"{path} response path")


def assert_equal(actual: Any, expected: Any, label: str) -> None:
    if actual != expected:
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")


def log(message: str) -> None:
    print(f"[coco-sample] {message}", flush=True)


def log_response(label: str, method: str, path: str, response: HttpResult) -> None:
    log(
        f"{label} {method} {path} -> status={response.status} "
        f"code={response.body.get('code')} success={response.body.get('success')} "
        f"traceId={response.body.get('traceId')} message={response.body.get('message')}"
    )


if __name__ == "__main__":
    sys.exit(main())
