#!/usr/bin/env python3
"""Run black-box checks against the Coco sample business application."""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
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

SAMPLE_APP_ID = "sample-app"
SAMPLE_SIGNATURE_SECRET = "sample-secret"
SAMPLE_AES_KEY = b"0123456789abcdef"
SAMPLE_AES_IV = b"123456789012"
ORDER_PATH = "/sample/orders"
SIGNATURE_ORDER_PATH = "/sample/secure/signature/orders"
REPLAY_ORDER_PATH = "/sample/secure/replay/orders"
ENCRYPTION_ORDER_PATH = "/sample/secure/encryption/orders"


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
    managed_application = base_url is None
    java_command = resolve_java_command(args.java_command) if managed_application else None
    if base_url is None:
        port = free_port()
        base_url = f"http://127.0.0.1:{port}"
        log(f"Starting sample jar: {java_command} -jar {args.jar} --server.port={port}")
        process = start_application(args.jar, port, java_command)
        wait_until_ready(base_url, args.timeout_seconds, process)
    if not args.skip_security_flow:
        log("Security flows run against the same application instance.")
    try:
        run_business_flow(base_url)
        if not args.skip_security_flow:
            run_signature_flow(base_url)
            run_replay_flow(base_url)
            run_encryption_flow(base_url)
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
    parser.add_argument(
        "--skip-security-flow",
        action="store_true",
        help="Skip Coco signature, replay, and encryption black-box checks.",
    )
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


def start_application(
    jar_path: Path,
    port: int,
    java_command: str,
    extra_args: list[str] | None = None,
) -> subprocess.Popen[str]:
    if not jar_path.is_file():
        raise AssertionError(f"Sample jar does not exist: {jar_path}")
    command = [java_command, "-jar", str(jar_path), f"--server.port={port}", *(extra_args or [])]
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
    assert_equal(insufficient.body["code"], 1004, "insufficient stock code")
    assert_equal(
        insufficient.body["message"],
        "\u5546\u54c1 COCO-STARTER \u5e93\u5b58\u4e0d\u8db3\uff0c"
        "\u5f53\u524d\u5e93\u5b58 3\uff0c\u8bf7\u6c42\u6570\u91cf 99",
        "insufficient stock zh-CN message",
    )
    assert_equal(insufficient.headers.get("X-Trace-Id"), "python-stock-error-zh", "insufficient stock zh-CN trace")
    assert_missing(insufficient.body, "traceId", "insufficient stock zh-CN body trace")
    assert_missing(insufficient.body, "path", "insufficient stock zh-CN body path")
    log("  stock error zh-CN: code=1004 status=409")

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
    assert_equal(english_insufficient.body["code"], 1004, "insufficient stock en-US code")
    assert_equal(
        english_insufficient.body["message"],
        "Product COCO-STARTER has insufficient stock, current stock 3, requested quantity 99",
        "insufficient stock en-US message",
    )
    assert_equal(
        english_insufficient.headers.get("X-Trace-Id"),
        "python-stock-error-en",
        "insufficient stock en-US trace",
    )
    assert_missing(english_insufficient.body, "traceId", "insufficient stock en-US body trace")
    assert_missing(english_insufficient.body, "path", "insufficient stock en-US body path")
    log("  stock error en-US: code=1004 status=409")


def run_signature_flow(base_url: str) -> None:
    log(f"Running signature flow against {base_url}")
    payload = {"buyerName": "Patton", "sku": "COCO-SIGNATURE", "quantity": 1}
    signed = signed_order_request(base_url, SIGNATURE_ORDER_PATH, payload, "python-signature-ok")
    log_response("SIGNATURE_OK", "POST", SIGNATURE_ORDER_PATH, signed)
    assert_success(signed, "python-signature-ok", SIGNATURE_ORDER_PATH)
    assert_equal(signed.body["data"]["status"], "CREATED", "signed order status")

    missing = request_json(
        base_url,
        SIGNATURE_ORDER_PATH,
        {"buyerName": "Patton", "sku": "COCO-SIGNATURE", "quantity": 1},
        trace_id="python-signature-missing",
        accept_language="en-US",
    )
    log_response("SIGNATURE_MISSING", "POST", SIGNATURE_ORDER_PATH, missing)
    assert_error(missing, 401, 401, "Request signature is missing.", "python-signature-missing", "missing signature")

    invalid = signed_order_request(
        base_url,
        SIGNATURE_ORDER_PATH,
        {"buyerName": "Patton", "sku": "COCO-SIGNATURE", "quantity": 1},
        "python-signature-invalid",
        tamper_signature=True,
        accept_language="en-US",
    )
    log_response("SIGNATURE_INVALID", "POST", SIGNATURE_ORDER_PATH, invalid)
    assert_error(invalid, 401, 401, "Request signature is invalid.", "python-signature-invalid", "invalid signature")


def run_replay_flow(base_url: str) -> None:
    log(f"Running replay flow against {base_url}")
    timestamp = str(int(time.time() * 1000))
    nonce = "python-replay-nonce"
    headers = replay_headers(timestamp, nonce)
    first = request_json(
        base_url,
        REPLAY_ORDER_PATH,
        {"buyerName": "Patton", "sku": "COCO-REPLAY", "quantity": 1},
        trace_id="python-replay-first",
        extra_headers=headers,
    )
    log_response("REPLAY_FIRST", "POST", REPLAY_ORDER_PATH, first)
    assert_success(first, "python-replay-first", REPLAY_ORDER_PATH)

    second = request_json(
        base_url,
        REPLAY_ORDER_PATH,
        {"buyerName": "Patton", "sku": "COCO-REPLAY", "quantity": 1},
        trace_id="python-replay-second",
        accept_language="en-US",
        extra_headers=headers,
    )
    log_response("REPLAY_DETECTED", "POST", REPLAY_ORDER_PATH, second)
    assert_error(second, 401, 401, "Request replay has been detected.", "python-replay-second", "replay detected")


def run_encryption_flow(base_url: str) -> None:
    log(f"Running encryption flow against {base_url}")
    payload = {"buyerName": "Patton", "sku": "COCO-ENCRYPTION", "quantity": 1}
    encrypted = encrypted_order_request(base_url, ENCRYPTION_ORDER_PATH, payload, "python-encryption-ok")
    log_response("ENCRYPTION_OK", "POST", ENCRYPTION_ORDER_PATH, encrypted)
    assert_success(encrypted, "python-encryption-ok", ENCRYPTION_ORDER_PATH)
    assert_equal(encrypted.body["data"]["status"], "CREATED", "encrypted order status")

    missing = request_json(
        base_url,
        ENCRYPTION_ORDER_PATH,
        {"buyerName": "Patton", "sku": "COCO-ENCRYPTION", "quantity": 1},
        trace_id="python-encryption-missing",
        accept_language="en-US",
    )
    log_response("ENCRYPTION_MISSING", "POST", ENCRYPTION_ORDER_PATH, missing)
    assert_error(
        missing,
        401,
        401,
        "Request encryption flag is missing.",
        "python-encryption-missing",
        "missing encryption flag",
    )

    invalid = request_body(
        "POST",
        base_url,
        ENCRYPTION_ORDER_PATH,
        b"invalid-payload",
        trace_id="python-encryption-invalid",
        accept_language="en-US",
        extra_headers=encryption_headers(),
    )
    log_response("ENCRYPTION_INVALID", "POST", ENCRYPTION_ORDER_PATH, invalid)
    assert_error(
        invalid,
        401,
        401,
        "Request encryption payload is invalid.",
        "python-encryption-invalid",
        "invalid encryption payload",
    )


def request(
    method: str,
    base_url: str,
    path: str,
    trace_id: str,
    accept_language: str | None = None,
    extra_headers: dict[str, str] | None = None,
) -> HttpResult:
    req = urllib.request.Request(base_url + path, method=method)
    req.add_header("Accept", "application/json")
    req.add_header("X-Trace-Id", trace_id)
    if accept_language:
        req.add_header("Accept-Language", accept_language)
    add_headers(req, extra_headers)
    return send(req)


def request_json(
    base_url: str,
    path: str,
    payload: dict[str, Any],
    trace_id: str,
    accept_language: str | None = None,
    extra_headers: dict[str, str] | None = None,
) -> HttpResult:
    data = json.dumps(payload).encode("utf-8")
    return request_body(
        "POST",
        base_url,
        path,
        data,
        trace_id,
        accept_language=accept_language,
        extra_headers=extra_headers,
    )


def request_body(
    method: str,
    base_url: str,
    path: str,
    data: bytes,
    trace_id: str,
    accept_language: str | None = None,
    extra_headers: dict[str, str] | None = None,
) -> HttpResult:
    req = urllib.request.Request(base_url + path, method=method, data=data)
    req.add_header("Accept", "application/json")
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Trace-Id", trace_id)
    if accept_language:
        req.add_header("Accept-Language", accept_language)
    add_headers(req, extra_headers)
    return send(req)


def add_headers(req: urllib.request.Request, headers: dict[str, str] | None) -> None:
    for name, value in (headers or {}).items():
        req.add_header(name, value)


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
    assert_equal(response.body["code"], 200, f"{path} response code")
    assert_equal(response.body["message"], "\u64cd\u4f5c\u6210\u529f", f"{path} response message")
    assert_missing(response.body, "traceId", f"{path} response body trace")
    assert_missing(response.body, "path", f"{path} response body path")


def assert_error(
    response: HttpResult,
    status: int,
    code: int,
    message: str,
    trace_id: str,
    label: str,
) -> None:
    assert_equal(response.status, status, f"{label} HTTP status")
    assert_equal(response.headers.get("X-Trace-Id"), trace_id, f"{label} trace header")
    assert_equal(response.body["success"], False, f"{label} success flag")
    assert_equal(response.body["code"], code, f"{label} response code")
    assert_equal(response.body["message"], message, f"{label} response message")
    assert_missing(response.body, "traceId", f"{label} response body trace")
    assert_missing(response.body, "path", f"{label} response body path")


def assert_equal(actual: Any, expected: Any, label: str) -> None:
    if actual != expected:
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")


def assert_missing(body: dict[str, Any], key: str, label: str) -> None:
    if key in body:
        raise AssertionError(f"{label}: expected missing key {key!r}, got {body[key]!r}")


def log(message: str) -> None:
    print(f"[coco-sample] {message}", flush=True)


def log_response(label: str, method: str, path: str, response: HttpResult) -> None:
    log(
        f"{label} {method} {path} -> status={response.status} "
        f"code={response.body.get('code')} success={response.body.get('success')} "
        f"traceId={response.headers.get('X-Trace-Id')} message={response.body.get('message')}"
    )


def signed_order_request(
    base_url: str,
    path: str,
    payload: dict[str, Any],
    trace_id: str,
    tamper_signature: bool = False,
    accept_language: str | None = None,
) -> HttpResult:
    timestamp = str(int(time.time() * 1000))
    nonce = trace_id + "-nonce"
    data = json.dumps(payload).encode("utf-8")
    headers = {
        "X-Coco-App-Id": SAMPLE_APP_ID,
        "X-Coco-Timestamp": timestamp,
        "X-Coco-Nonce": nonce,
        "X-Coco-Sign-Algorithm": "HMAC-SHA256",
    }
    signature = sign_order_payload(path, headers, data, payload)
    headers["X-Coco-Sign"] = "invalid-" + signature if tamper_signature else signature
    return request_body(
        "POST",
        base_url,
        path,
        data,
        trace_id,
        accept_language=accept_language,
        extra_headers=headers,
    )


def sign_order_payload(path: str, headers: dict[str, str], data: bytes, payload: dict[str, Any]) -> str:
    canonical_headers = {
        "content-type": ["application/json"],
        "x-coco-app-id": [headers["X-Coco-App-Id"]],
        "x-coco-nonce": [headers["X-Coco-Nonce"]],
        "x-coco-sign-algorithm": [headers["X-Coco-Sign-Algorithm"]],
        "x-coco-timestamp": [headers["X-Coco-Timestamp"]],
    }
    canonical_text = canonical_text_for_json_payload(
        method="POST",
        path=path,
        query_string=None,
        headers=canonical_headers,
        payload_parameters=flatten_json_payload(payload),
        body=data,
    )
    return hmac.new(
        SAMPLE_SIGNATURE_SECRET.encode("utf-8"),
        canonical_text.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def replay_headers(timestamp: str, nonce: str) -> dict[str, str]:
    return {
        "X-Coco-App-Id": SAMPLE_APP_ID,
        "X-Coco-Timestamp": timestamp,
        "X-Coco-Nonce": nonce,
    }


def encrypted_order_request(base_url: str, path: str, payload: dict[str, Any], trace_id: str) -> HttpResult:
    data = json.dumps(payload).encode("utf-8")
    headers = encryption_headers()
    aad = encryption_associated_data(
        app_id=SAMPLE_APP_ID,
        key_id=None,
        iv=headers["X-Coco-IV"],
        algorithm="AES-GCM",
        encrypted=True,
        method="POST",
        path=path,
        query_string=None,
        replay_timestamp=None,
        replay_nonce=None,
    )
    ciphertext = aes_gcm_encrypt(data, SAMPLE_AES_KEY, SAMPLE_AES_IV, aad)
    transport_body = base64.b64encode(ciphertext)
    return request_body(
        "POST",
        base_url,
        path,
        transport_body,
        trace_id,
        extra_headers=headers,
    )


def encryption_headers() -> dict[str, str]:
    return {
        "X-Coco-Encrypted": "true",
        "X-Coco-App-Id": SAMPLE_APP_ID,
        "X-Coco-IV": base64.b64encode(SAMPLE_AES_IV).decode("ascii"),
        "X-Coco-Algorithm": "AES-GCM",
    }


def aes_gcm_encrypt(plain_body: bytes, key: bytes, iv: bytes, aad: bytes) -> bytes:
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError as exc:
        raise AssertionError("Python package 'cryptography' is required for Coco AES-GCM sample checks.") from exc
    return AESGCM(key).encrypt(iv, plain_body, aad)


def canonical_text_for_json_payload(
    method: str,
    path: str,
    query_string: str | None,
    headers: dict[str, list[str]],
    payload_parameters: dict[str, list[str]],
    body: bytes,
) -> str:
    lines: list[str] = [
        "version=coco-v2",
        "purpose=SIGNATURE",
        f"method={escape_value(method)}",
        f"path={escape_value(path)}",
        f"query={escape_value(query_string)}",
        "headers",
    ]
    for name in sorted(headers):
        values = headers[name] or [""]
        lines.append(f"{escape_value(name)}#{len(values)}")
        for index, value in enumerate(values):
            lines.append(f"{escape_value(name)}[{index}]={framed_value(value)}")
    lines.append("queryParameters")
    lines.append("payloadParameters")
    for name in sorted(payload_parameters):
        values = sorted(payload_parameters[name] or [""])
        lines.append(f"{escape_value(name)}#{len(values)}")
        for index, value in enumerate(values):
            lines.append(f"{escape_value(name)}[{index}]={framed_value(value)}")
    lines.append("bodySha256=" + hashlib.sha256(body).hexdigest())
    lines.append("bodyLength=" + str(len(body)))
    return "\n".join(lines) + "\n"


def flatten_json_payload(payload: dict[str, Any]) -> dict[str, list[str]]:
    parameters: dict[str, list[str]] = {}
    flatten_json_value(parameters, "", payload)
    return parameters


def flatten_json_value(parameters: dict[str, list[str]], path: str, value: Any) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = str(key).strip() if not path else path + "." + str(key).strip()
            flatten_json_value(parameters, child_path, child)
        return
    if isinstance(value, list):
        for index, child in enumerate(value):
            if isinstance(child, (dict, list)):
                flatten_json_value(parameters, f"{path or '$'}[{index}]", child)
            else:
                parameters.setdefault(path or "$", []).append(json_scalar_value(child))
        return
    parameters.setdefault(path or "$", []).append(json_scalar_value(value))


def json_scalar_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return str(value).lower()
    return str(value)


def encryption_associated_data(
    app_id: str | None,
    key_id: str | None,
    iv: str | None,
    algorithm: str | None,
    encrypted: bool,
    method: str | None,
    path: str | None,
    query_string: str | None,
    replay_timestamp: str | None,
    replay_nonce: str | None,
) -> bytes:
    lines = ["coco.web.encryption.v1"]
    lines.append(aad_field("appId", app_id))
    lines.append(aad_field("keyId", key_id))
    lines.append(aad_field("iv", iv))
    lines.append(aad_field("algorithm", algorithm))
    lines.append(aad_field("encrypted", str(encrypted).lower()))
    lines.append(aad_field("method", method))
    lines.append(aad_field("path", path))
    lines.append(aad_field("query", query_string))
    lines.append(aad_field("replayTimestamp", replay_timestamp))
    lines.append(aad_field("replayNonce", replay_nonce))
    return ("\n".join(lines) + "\n").encode("utf-8")


def aad_field(name: str, value: str | None) -> str:
    normalized = "" if value is None else value.strip()
    return f"{name}={len(normalized)}:{normalized}"


def framed_value(value: str | None) -> str:
    escaped = escape_value(value)
    return f"{len(escaped)}:{escaped}"


def escape_value(value: str | None) -> str:
    if value is None:
        return ""
    escaped: list[str] = []
    for char in value:
        if char == "\\":
            escaped.append("\\\\")
        elif char == "\n":
            escaped.append("\\n")
        elif char == "\r":
            escaped.append("\\r")
        elif char == ":":
            escaped.append("\\:")
        elif char == "=":
            escaped.append("\\=")
        elif char == ",":
            escaped.append("\\,")
        elif char == ";":
            escaped.append("\\;")
        elif char == "|":
            escaped.append("\\|")
        else:
            escaped.append(char)
    return "".join(escaped)


if __name__ == "__main__":
    sys.exit(main())
