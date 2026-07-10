#!/usr/bin/env python3
"""Generate importable Postman assets for the Coco sample application."""

from __future__ import annotations

import argparse
import base64
import binascii
import json
import os
import sys
import uuid
from pathlib import Path
from typing import Any

SAMPLE_APP_ID = "sample-app"
SAMPLE_SIGNING_KEY_ENV = "SAMPLE_SIGNING_KEY"
SAMPLE_ENCRYPTION_KEY_ENV = "SAMPLE_ENCRYPTION_KEY"
ORDER_PATH = "/sample/orders"
SIGNATURE_ORDER_PATH = "/sample/secure/signature/orders"
REPLAY_ORDER_PATH = "/sample/secure/replay/orders"
ENCRYPTION_ORDER_PATH = "/sample/secure/encryption/orders"
PRODUCTS_PATH = "/sample/products"


def main() -> int:
    configure_output_encoding()
    args = parse_args()
    output_dir = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    collection_path = output_dir / "coco-sample-basic.postman_collection.json"
    environment_path = output_dir / "coco-sample-basic.postman_environment.json"

    signature_secret = os.environ.get(SAMPLE_SIGNING_KEY_ENV, "").strip()
    aes_key = resolve_encryption_key(os.environ.get(SAMPLE_ENCRYPTION_KEY_ENV))
    aes_iv = os.urandom(12) if aes_key is not None else None
    aes_iv_base64 = "" if aes_iv is None else base64.b64encode(aes_iv).decode("ascii")
    encrypted_body = "" if aes_key is None or aes_iv is None else encrypted_order_body(aes_key, aes_iv)
    postman_collection = collection(args.base_url, aes_iv_base64, encrypted_body)
    postman_environment = environment(args.base_url, signature_secret, aes_iv_base64, encrypted_body)
    validate_assets(postman_collection, postman_environment)
    collection_path.write_text(
        json.dumps(postman_collection, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    environment_path.write_text(
        json.dumps(postman_environment, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"Postman 集合已生成：{collection_path}")
    print(f"Postman 环境已生成：{environment_path}")
    print(f"请求数量：{count_requests(postman_collection)}")
    print(f"环境变量：{len(postman_environment['values'])}")
    if signature_secret:
        print("警告：Postman 环境文件包含 SAMPLE_SIGNING_KEY，请勿提交或共享该文件。")
    if aes_key is None:
        print("提示：未设置 SAMPLE_ENCRYPTION_KEY，加密请求体保持为空。")
    return 0


def parse_args() -> argparse.Namespace:
    sample_dir = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description="Generate Coco sample Postman import files.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Default sample application URL.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=sample_dir / "target" / "postman",
        help="Directory where local Postman import files are written (default: target/postman).",
    )
    return parser.parse_args()


def collection(base_url: str, aes_iv_base64: str, encrypted_body: str) -> dict[str, Any]:
    return {
        "info": {
            "_postman_id": deterministic_id("collection:coco-sample-basic"),
            "name": "Coco Sample Basic",
            "description": (
                "Coco 示例基础业务接口测试集。导入后选择 Coco Sample Basic Local 环境，"
                "或修改 collection variable 中的 baseUrl。安全接口需要先配置示例进程和 Postman 环境密钥。"
            ),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "auth": {"type": "noauth"},
        "event": [collection_prerequest_event()],
        "variable": collection_variables(base_url, aes_iv_base64, encrypted_body),
        "item": [
            business_folder(),
            i18n_error_folder(),
            signature_folder(),
            replay_folder(),
            encryption_folder(),
        ],
    }


def environment(base_url: str, signature_secret: str, aes_iv_base64: str,
        encrypted_body: str) -> dict[str, Any]:
    return {
        "id": deterministic_id("environment:coco-sample-basic-local"),
        "name": "Coco Sample Basic Local",
        "values": [
            env_value("baseUrl", base_url),
            env_value("orderId", ""),
            env_value("secureAppId", SAMPLE_APP_ID),
            env_value("signatureSecret", signature_secret, "secret"),
            env_value("replayTimestamp", ""),
            env_value("replayNonce", ""),
            env_value("aesIvBase64", aes_iv_base64),
            env_value("encryptedOrderBody", encrypted_body),
        ],
        "_postman_variable_scope": "environment",
        "_postman_exported_using": "Coco Sample Postman Generator",
    }


def validate_assets(postman_collection: dict[str, Any], postman_environment: dict[str, Any]) -> None:
    expected_folders = ["业务主流程", "异常与国际化", "签名模式", "防重放模式", "加密模式"]
    folder_names = [folder["name"] for folder in postman_collection["item"]]
    if folder_names != expected_folders:
        raise AssertionError(f"Unexpected Postman folders: {folder_names}")

    request_count = count_requests(postman_collection)
    if request_count != 16:
        raise AssertionError(f"Unexpected Postman request count: {request_count}")

    required_values = {
        "baseUrl",
        "orderId",
        "secureAppId",
        "signatureSecret",
        "replayTimestamp",
        "replayNonce",
        "aesIvBase64",
        "encryptedOrderBody",
    }
    actual_values = {value["key"] for value in postman_environment["values"]}
    missing_values = sorted(required_values - actual_values)
    if missing_values:
        raise AssertionError(f"Missing Postman environment values: {missing_values}")


def count_requests(postman_collection: dict[str, Any]) -> int:
    return sum(len(folder.get("item", [])) for folder in postman_collection["item"])


def configure_output_encoding() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if callable(reconfigure):
            reconfigure(encoding="utf-8", errors="replace")


def collection_variables(base_url: str, aes_iv_base64: str,
        encrypted_body: str) -> list[dict[str, str]]:
    return [
        variable("baseUrl", base_url),
        variable("orderId", ""),
        variable("secureAppId", SAMPLE_APP_ID),
        variable("replayTimestamp", ""),
        variable("replayNonce", ""),
        variable("aesIvBase64", aes_iv_base64),
        variable("encryptedOrderBody", encrypted_body),
    ]


def business_folder() -> dict[str, Any]:
    return folder(
        "业务主流程",
        "按顺序执行：查询商品、创建订单、使用保存的 orderId 查询订单。",
        [
            request_item(
                "查询商品库存",
                "GET",
                PRODUCTS_PATH,
                "postman-products",
                tests=[
                    "const body = pm.response.json();",
                    test_status(200),
                    test_success("postman-products"),
                    test_no_response_metadata(),
                    test_block(
                        "包含示例商品 COCO-STARTER",
                        [
                            'pm.expect(body.data).to.be.an("array").that.is.not.empty;',
                            'const starter = body.data.find(item => item.sku === "COCO-STARTER");',
                            "pm.expect(starter).to.exist;",
                            'pm.expect(starter.name).to.eql("Coco Starter");',
                            "pm.expect(starter.unitPrice).to.eql(9900);",
                        ],
                    ),
                ],
            ),
            request_item(
                "创建订单",
                "POST",
                ORDER_PATH,
                "postman-create-order",
                body={"buyerName": "Patton", "sku": "COCO-STARTER", "quantity": 2},
                tests=[
                    "const body = pm.response.json();",
                    test_status(200),
                    test_success("postman-create-order"),
                    test_no_response_metadata(),
                    test_block(
                        "创建订单成功并写入 orderId 变量",
                        [
                            'pm.expect(body.data.orderId).to.match(/^ORD-\\d+$/);',
                            'pm.collectionVariables.set("orderId", body.data.orderId);',
                            'pm.environment.set("orderId", body.data.orderId);',
                            'pm.expect(body.data.buyerName).to.eql("Patton");',
                            'pm.expect(body.data.sku).to.eql("COCO-STARTER");',
                            "pm.expect(body.data.quantity).to.eql(2);",
                            "pm.expect(body.data.totalAmount).to.eql(19800);",
                            'pm.expect(body.data.status).to.eql("CREATED");',
                        ],
                    ),
                ],
            ),
            request_item(
                "查询订单详情",
                "GET",
                "/sample/orders/{{orderId}}",
                "postman-load-order",
                tests=[
                    "const body = pm.response.json();",
                    'const orderId = pm.collectionVariables.get("orderId") || pm.environment.get("orderId");',
                    test_status(200),
                    test_success("postman-load-order"),
                    test_no_response_metadata(),
                    test_block(
                        "按 orderId 查询订单成功",
                        [
                            'pm.expect(orderId, "请先运行创建订单请求").to.not.be.empty;',
                            "pm.expect(body.data.orderId).to.eql(orderId);",
                            'pm.expect(body.data.buyerName).to.eql("Patton");',
                        ],
                    ),
                ],
            ),
        ],
    )


def i18n_error_folder() -> dict[str, Any]:
    return folder(
        "异常与国际化",
        "验证业务异常进入 Coco 统一异常响应，并按 Accept-Language 返回国际化消息。",
        [
            request_item(
                "库存不足 zh-CN",
                "POST",
                ORDER_PATH,
                "postman-stock-error-zh",
                accept_language="zh-CN",
                body={"buyerName": "Patton", "sku": "COCO-STARTER", "quantity": 99},
                tests=error_tests(409, 1004, "postman-stock-error-zh", ["商品 COCO-STARTER 库存不足", "请求数量 99"]),
            ),
            request_item(
                "库存不足 en-US",
                "POST",
                ORDER_PATH,
                "postman-stock-error-en",
                accept_language="en-US",
                body={"buyerName": "Patton", "sku": "COCO-STARTER", "quantity": 99},
                tests=error_tests(
                    409,
                    1004,
                    "postman-stock-error-en",
                    ["Product COCO-STARTER has insufficient stock", "requested quantity 99"],
                ),
            ),
            request_item(
                "订单不存在 zh-CN",
                "GET",
                "/sample/orders/ORD-NOT-FOUND",
                "postman-order-not-found",
                accept_language="zh-CN",
                tests=error_tests(404, 1002, "postman-order-not-found", ["订单不存在", "ORD-NOT-FOUND"]),
            ),
            request_item(
                "商品不存在 zh-CN",
                "POST",
                ORDER_PATH,
                "postman-product-not-found",
                accept_language="zh-CN",
                body={"buyerName": "Patton", "sku": "COCO-MISSING", "quantity": 1},
                tests=error_tests(404, 1001, "postman-product-not-found", ["商品不存在", "COCO-MISSING"]),
            ),
            request_item(
                "下单数量不合法 zh-CN",
                "POST",
                ORDER_PATH,
                "postman-invalid-quantity",
                accept_language="zh-CN",
                body={"buyerName": "Patton", "sku": "COCO-STARTER", "quantity": 0},
                tests=error_tests(400, 1003, "postman-invalid-quantity", ["下单数量不合法", "quantity"]),
            ),
        ],
    )


def signature_folder() -> dict[str, Any]:
    return folder(
        "签名模式",
        "配置 SAMPLE_SIGNING_KEY 后，对签名保护订单接口执行验签场景。",
        [
            request_item(
                "签名订单成功",
                "POST",
                SIGNATURE_ORDER_PATH,
                "postman-signature-ok",
                body={"buyerName": "Patton", "sku": "COCO-SIGNATURE", "quantity": 1},
                prerequest=signature_prerequest_script(SIGNATURE_ORDER_PATH, False),
                tests=[success_order_test("postman-signature-ok")],
            ),
            request_item(
                "缺失签名",
                "POST",
                SIGNATURE_ORDER_PATH,
                "postman-signature-missing",
                accept_language="en-US",
                body={"buyerName": "Patton", "sku": "COCO-SIGNATURE", "quantity": 1},
                tests=error_tests(401, 401, "postman-signature-missing", ["Request signature is missing."]),
            ),
            request_item(
                "签名错误",
                "POST",
                SIGNATURE_ORDER_PATH,
                "postman-signature-invalid",
                accept_language="en-US",
                body={"buyerName": "Patton", "sku": "COCO-SIGNATURE", "quantity": 1},
                prerequest=signature_prerequest_script(SIGNATURE_ORDER_PATH, True),
                tests=error_tests(401, 401, "postman-signature-invalid", ["Request signature is invalid."]),
            ),
        ],
    )


def replay_folder() -> dict[str, Any]:
    return folder(
        "防重放模式",
        "同一个示例应用默认启动后，对防重放保护订单接口执行首次提交和重复提交场景。",
        [
            request_item(
                "防重放首次请求",
                "POST",
                REPLAY_ORDER_PATH,
                "postman-replay-first",
                body={"buyerName": "Patton", "sku": "COCO-REPLAY", "quantity": 1},
                prerequest=replay_first_prerequest_script(),
                tests=[success_order_test("postman-replay-first")],
            ),
            request_item(
                "防重放重复请求",
                "POST",
                REPLAY_ORDER_PATH,
                "postman-replay-second",
                accept_language="en-US",
                body={"buyerName": "Patton", "sku": "COCO-REPLAY", "quantity": 1},
                prerequest=replay_second_prerequest_script(),
                tests=error_tests(401, 401, "postman-replay-second", ["Request replay has been detected."]),
            ),
        ],
    )


def encryption_folder() -> dict[str, Any]:
    return folder(
        "加密模式",
        "配置 SAMPLE_ENCRYPTION_KEY 并重新生成 Postman 资产后，对加密保护订单接口执行解密和异常场景。",
        [
            request_item(
                "加密订单成功",
                "POST",
                ENCRYPTION_ORDER_PATH,
                "postman-encryption-ok",
                raw_body="{{encryptedOrderBody}}",
                extra_headers=encryption_headers(),
                tests=[success_order_test("postman-encryption-ok")],
            ),
            request_item(
                "缺失加密标记",
                "POST",
                ENCRYPTION_ORDER_PATH,
                "postman-encryption-missing",
                accept_language="en-US",
                body={"buyerName": "Patton", "sku": "COCO-ENCRYPTION", "quantity": 1},
                tests=error_tests(401, 401, "postman-encryption-missing", ["Request encryption flag is missing."]),
            ),
            request_item(
                "加密内容非法",
                "POST",
                ENCRYPTION_ORDER_PATH,
                "postman-encryption-invalid",
                accept_language="en-US",
                raw_body="invalid-payload",
                extra_headers=encryption_headers(),
                tests=error_tests(400, 400, "postman-encryption-invalid", ["Request encryption data is malformed."]),
            ),
        ],
    )


def request_item(
    name: str,
    method: str,
    path: str,
    trace_id: str,
    body: dict[str, Any] | None = None,
    raw_body: str | None = None,
    accept_language: str | None = None,
    extra_headers: list[dict[str, str]] | None = None,
    prerequest: list[str] | None = None,
    tests: list[str] | None = None,
) -> dict[str, Any]:
    headers = [
        {"key": "Accept", "value": "application/json"},
        {"key": "X-Trace-Id", "value": trace_id},
    ]
    if accept_language:
        headers.append({"key": "Accept-Language", "value": accept_language})
    request: dict[str, Any] = {
        "method": method,
        "header": headers,
        "url": "{{baseUrl}}" + path,
    }
    if body is not None or raw_body is not None:
        headers.append({"key": "Content-Type", "value": "application/json"})
        request["body"] = {
            "mode": "raw",
            "raw": raw_body if raw_body is not None else json.dumps(body, ensure_ascii=False, indent=2),
            "options": {"raw": {"language": "json"}},
        }
    if extra_headers:
        headers.extend(extra_headers)
    events = []
    if prerequest:
        events.append(script_event("prerequest", prerequest))
    if tests:
        events.append(script_event("test", flatten_scripts(tests)))
    return {"name": name, "request": request, "response": [], "event": events}


def folder(name: str, description: str, items: list[dict[str, Any]]) -> dict[str, Any]:
    return {"name": name, "description": description, "item": items}


def script_event(listen: str, exec_lines: list[str]) -> dict[str, Any]:
    return {"listen": listen, "script": {"type": "text/javascript", "exec": exec_lines}}


def collection_prerequest_event() -> dict[str, Any]:
    return script_event(
        "prerequest",
        [
            'if (!pm.collectionVariables.get("baseUrl")) {',
            '    pm.collectionVariables.set("baseUrl", "http://localhost:8080");',
            "}",
            '["secureAppId", "aesIvBase64", "encryptedOrderBody"].forEach(function (key) {',
            "    const envValue = pm.environment.get(key);",
            "    if (envValue && !pm.collectionVariables.get(key)) {",
            "        pm.collectionVariables.set(key, envValue);",
            "    }",
            "});",
        ],
    )


def signature_prerequest_script(path: str, tamper_signature: bool) -> list[str]:
    tamper_line = (
        'pm.request.headers.upsert({ key: "X-Coco-Sign", value: "invalid-" + signature });'
        if tamper_signature
        else 'pm.request.headers.upsert({ key: "X-Coco-Sign", value: signature });'
    )
    return [
        'const crypto = typeof CryptoJS !== "undefined" ? CryptoJS : require("crypto-js");',
        'const appId = pm.collectionVariables.get("secureAppId") || pm.environment.get("secureAppId") || "sample-app";',
        'const secret = pm.environment.get("signatureSecret");',
        'pm.expect(secret, "请在 Postman 环境中配置 signatureSecret").to.not.be.empty;',
        'const timestamp = Date.now().toString();',
        'const nonce = "postman-signature-" + timestamp;',
        'pm.request.headers.upsert({ key: "X-Coco-App-Id", value: appId });',
        'pm.request.headers.upsert({ key: "X-Coco-Timestamp", value: timestamp });',
        'pm.request.headers.upsert({ key: "X-Coco-Nonce", value: nonce });',
        'pm.request.headers.upsert({ key: "X-Coco-Sign-Algorithm", value: "HMAC-SHA256" });',
        'const rawBody = pm.request.body && pm.request.body.raw ? pm.request.body.raw : "";',
        "const payload = JSON.parse(rawBody);",
        "function escapeValue(value) {",
        '    if (value === null || value === undefined) { return ""; }',
        "    return String(value)",
        '        .replace(/\\\\/g, "\\\\\\\\")',
        '        .replace(/\\n/g, "\\\\n")',
        '        .replace(/\\r/g, "\\\\r")',
        '        .replace(/:/g, "\\\\:")',
        '        .replace(/=/g, "\\\\=")',
        '        .replace(/,/g, "\\\\,")',
        '        .replace(/;/g, "\\\\;")',
        '        .replace(/\\|/g, "\\\\|");',
        "}",
        "function framedValue(value) {",
        "    const escaped = escapeValue(value);",
        '    return escaped.length + ":" + escaped;',
        "}",
        "function scalarValue(value) {",
        '    if (value === null || value === undefined) { return ""; }',
        '    if (typeof value === "boolean") { return value ? "true" : "false"; }',
        "    return String(value);",
        "}",
        "function flatten(target, path, value) {",
        '    if (Array.isArray(value)) {',
        "        value.forEach(function (child, index) {",
        '            if (child !== null && typeof child === "object") {',
        '                flatten(target, (path || "$") + "[" + index + "]", child);',
        "            } else {",
        '                const name = path || "$";',
        "                target[name] = target[name] || [];",
        "                target[name].push(scalarValue(child));",
        "            }",
        "        });",
        "        return;",
        "    }",
        '    if (value !== null && typeof value === "object") {',
        "        Object.keys(value).forEach(function (key) {",
        '            flatten(target, path ? path + "." + key.trim() : key.trim(), value[key]);',
        "        });",
        "        return;",
        "    }",
        '    const name = path || "$";',
        "    target[name] = target[name] || [];",
        "    target[name].push(scalarValue(value));",
        "}",
        "const params = {};",
        "flatten(params, \"\", payload);",
        "const bodyWordArray = crypto.enc.Utf8.parse(rawBody);",
        "const lines = [",
        '    "version=coco-v2",',
        '    "purpose=SIGNATURE",',
        '    "method=POST",',
        f'    "path={path}",',
        '    "query=",',
        '    "headers",',
        '    "content-type#1",',
        '    "content-type[0]=" + framedValue("application/json"),',
        '    "x-coco-app-id#1",',
        '    "x-coco-app-id[0]=" + framedValue(appId),',
        '    "x-coco-nonce#1",',
        '    "x-coco-nonce[0]=" + framedValue(nonce),',
        '    "x-coco-sign-algorithm#1",',
        '    "x-coco-sign-algorithm[0]=" + framedValue("HMAC-SHA256"),',
        '    "x-coco-timestamp#1",',
        '    "x-coco-timestamp[0]=" + framedValue(timestamp),',
        '    "queryParameters",',
        '    "payloadParameters"',
        "];",
        "Object.keys(params).sort().forEach(function (name) {",
        "    const values = params[name].slice().sort();",
        '    lines.push(escapeValue(name) + "#" + values.length);',
        "    values.forEach(function (value, index) {",
        '        lines.push(escapeValue(name) + "[" + index + "]=" + framedValue(value));',
        "    });",
        "});",
        'lines.push("bodySha256=" + crypto.SHA256(bodyWordArray).toString(crypto.enc.Hex));',
        'lines.push("bodyLength=" + bodyWordArray.sigBytes);',
        'const canonicalText = lines.join("\\n") + "\\n";',
        "const signature = crypto.HmacSHA256(canonicalText, secret).toString(crypto.enc.Hex);",
        tamper_line,
    ]


def replay_first_prerequest_script() -> list[str]:
    return [
        'const appId = pm.collectionVariables.get("secureAppId") || pm.environment.get("secureAppId") || "sample-app";',
        'const timestamp = Date.now().toString();',
        'const nonce = "postman-replay-" + timestamp;',
        'pm.collectionVariables.set("replayTimestamp", timestamp);',
        'pm.collectionVariables.set("replayNonce", nonce);',
        'pm.environment.set("replayTimestamp", timestamp);',
        'pm.environment.set("replayNonce", nonce);',
        'pm.request.headers.upsert({ key: "X-Coco-App-Id", value: appId });',
        'pm.request.headers.upsert({ key: "X-Coco-Timestamp", value: timestamp });',
        'pm.request.headers.upsert({ key: "X-Coco-Nonce", value: nonce });',
    ]


def replay_second_prerequest_script() -> list[str]:
    return [
        'const appId = pm.collectionVariables.get("secureAppId") || pm.environment.get("secureAppId") || "sample-app";',
        'const timestamp = pm.collectionVariables.get("replayTimestamp") || pm.environment.get("replayTimestamp");',
        'const nonce = pm.collectionVariables.get("replayNonce") || pm.environment.get("replayNonce");',
        'pm.expect(timestamp, "请先运行防重放首次请求").to.not.be.empty;',
        'pm.expect(nonce, "请先运行防重放首次请求").to.not.be.empty;',
        'pm.request.headers.upsert({ key: "X-Coco-App-Id", value: appId });',
        'pm.request.headers.upsert({ key: "X-Coco-Timestamp", value: timestamp });',
        'pm.request.headers.upsert({ key: "X-Coco-Nonce", value: nonce });',
    ]


def encryption_headers() -> list[dict[str, str]]:
    return [
        {"key": "X-Coco-Encrypted", "value": "true"},
        {"key": "X-Coco-App-Id", "value": "{{secureAppId}}"},
        {"key": "X-Coco-IV", "value": "{{aesIvBase64}}"},
        {"key": "X-Coco-Algorithm", "value": "AES-GCM"},
    ]


def error_tests(status: int, code: int, trace_id: str, message_fragments: list[str]) -> list[str]:
    lines = [
        "const body = pm.response.json();",
        test_status(status),
        test_block(
            "返回 Coco 统一错误响应",
            [
                "pm.expect(body.success).to.eql(false);",
                f"pm.expect(body.code).to.eql({code});",
                f'pm.expect(pm.response.headers.get("X-Trace-Id")).to.eql("{trace_id}");',
                'pm.expect(body).to.not.have.property("traceId");',
                'pm.expect(body).to.not.have.property("path");',
            ],
        ),
    ]
    for fragment in message_fragments:
        lines.append(test_block(f"消息包含 {fragment}", [f'pm.expect(body.message).to.include("{fragment}");']))
    return lines


def success_order_test(trace_id: str) -> str:
    return "\n".join(
        [
            "const body = pm.response.json();",
            test_status(200),
            test_success(trace_id),
            test_no_response_metadata(),
            test_block(
                "订单创建成功",
                [
                    'pm.expect(body.data.orderId).to.match(/^ORD-\\d+$/);',
                    'pm.expect(body.data.status).to.eql("CREATED");',
                ],
            ),
        ]
    )


def test_status(status: int) -> str:
    return test_block(f"HTTP {status}", [f"pm.response.to.have.status({status});"])


def test_success(trace_id: str) -> str:
    return test_block(
        "返回 Coco 统一成功响应",
        [
            "pm.expect(body.success).to.eql(true);",
            "pm.expect(body.code).to.eql(200);",
            'pm.expect(body.message).to.eql("操作成功");',
            f'pm.expect(pm.response.headers.get("X-Trace-Id")).to.eql("{trace_id}");',
        ],
    )


def test_no_response_metadata() -> str:
    return test_block(
        "响应体不暴露 traceId 和 path",
        [
            'pm.expect(body).to.not.have.property("traceId");',
            'pm.expect(body).to.not.have.property("path");',
        ],
    )


def test_block(name: str, lines: list[str]) -> str:
    body = "\n".join("    " + line for line in lines)
    return f'pm.test("{name}", function () {{\n{body}\n}});'


def flatten_scripts(scripts: list[str]) -> list[str]:
    flattened: list[str] = []
    for script in scripts:
        flattened.extend(script.splitlines())
        flattened.append("")
    return flattened[:-1] if flattened else []


def variable(key: str, value: str) -> dict[str, str]:
    return {"key": key, "value": value, "type": "string"}


def env_value(key: str, value: str, value_type: str = "default") -> dict[str, Any]:
    return {"key": key, "value": value, "type": value_type, "enabled": True}


def deterministic_id(seed: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "https://github.com/patton174/coco-framework/" + seed))


def encrypted_order_body(aes_key: bytes, aes_iv: bytes) -> str:
    payload = json.dumps({"buyerName": "Patton", "sku": "COCO-ENCRYPTION", "quantity": 1}).encode("utf-8")
    aad = encryption_associated_data(
        app_id=SAMPLE_APP_ID,
        key_id=None,
        iv=base64.b64encode(aes_iv).decode("ascii"),
        algorithm="AES-GCM",
        encrypted=True,
        method="POST",
        path=ENCRYPTION_ORDER_PATH,
        query_string=None,
        replay_timestamp=None,
        replay_nonce=None,
    )
    return base64.b64encode(aes_gcm_encrypt(payload, aes_key, aes_iv, aad)).decode("ascii")


def resolve_encryption_key(encoded_key: str | None) -> bytes | None:
    if encoded_key is None or not encoded_key.strip():
        return None
    try:
        key = base64.b64decode(encoded_key.strip(), validate=True)
    except (ValueError, binascii.Error) as exc:
        raise SystemExit(f"{SAMPLE_ENCRYPTION_KEY_ENV} must be valid Base64.") from exc
    if len(key) not in (16, 24, 32):
        raise SystemExit(f"{SAMPLE_ENCRYPTION_KEY_ENV} must decode to 16, 24, or 32 bytes.")
    return key


def aes_gcm_encrypt(plain_body: bytes, key: bytes, iv: bytes, aad: bytes) -> bytes:
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError as exc:
        raise SystemExit("Python package 'cryptography' is required to generate the encrypted Postman request.") from exc
    return AESGCM(key).encrypt(iv, plain_body, aad)


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


if __name__ == "__main__":
    raise SystemExit(main())
