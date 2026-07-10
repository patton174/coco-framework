#!/usr/bin/env bash
# Call the Claude API (via a relay or the official endpoint) with a PR diff and
# print the model's text reply to stdout. The caller is responsible for posting
# the result to the PR.
#
# Why a hand-rolled script and not anthropics/claude-code-action:
#   The action performs an OIDC -> GitHub App token exchange to post PR comments
#   and requires the official Claude Code GitHub App to be installed on the
#   repository. When using a third-party relay with its own ANTHROPIC_API_KEY,
#   that App is not installable, so the action fails with 401 before any model
#   call is made. This script makes a raw /v1/messages call and lets the caller
#   post the comment via the standard workflow GITHUB_TOKEN.
#
# Required env:
#   ANTHROPIC_API_KEY   - relay or official key
#   ANTHROPIC_BASE_URL  - relay base URL (default: https://api.anthropic.com)
#   CLAUDE_MODEL        - model id available on the relay
# Argument:
#   $1                  - path to a file containing the PR diff (plain text)
# Stdout:
#   The model's reply text. On any non-recoverable error: empty stdout and a
#   message on stderr, plus a non-zero exit code.

set -euo pipefail

DIFF_FILE="${1:?usage: claude-review.sh <diff-file>}"

if [[ ! -s "${DIFF_FILE}" ]]; then
  echo "diff file is empty; nothing to review" >&2
  exit 0
fi

ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY:?ANTHROPIC_API_KEY is required}"
ANTHROPIC_BASE_URL="${ANTHROPIC_BASE_URL:-https://api.anthropic.com}"
CLAUDE_MODEL="${CLAUDE_MODEL:-claude-sonnet-4-6}"

# Normalize base URL: drop trailing slash and ensure it ends with /v1.
BASE_URL="${ANTHROPIC_BASE_URL%/}"
case "${BASE_URL}" in
  */v1) ;;
  *) BASE_URL="${BASE_URL}/v1" ;;
esac

# Cap the diff at ~60K characters so we leave room in the context for the
# prompt and the model's response. Use awk to count characters rather than
# bash ${#} which counts bytes — multi-byte text (CJK, non-ASCII paths) would
# otherwise truncate earlier than the documented budget.
MAX_DIFF_CHARS=60000
# `$(< file)` is faster than $(cat file) and preserves trailing newlines
# (which $(...) command substitution would strip).
DIFF_CONTENT="$(< "${DIFF_FILE}")"
DIFF_CHAR_COUNT="$(printf '%s' "${DIFF_CONTENT}" | awk '{ total += length($0) + 1 } END { print total + 0 }')"
if (( DIFF_CHAR_COUNT > MAX_DIFF_CHARS )); then
  DIFF_CONTENT="${DIFF_CONTENT:0:MAX_DIFF_CHARS}
... (diff truncated for review, see full diff on the PR)"
fi

read -r -d '' PROMPT <<'EOF' || true
You are reviewing a pull request for the Coco Framework, a convention-driven
Spring Boot server framework published to Maven Central. The diff to review
follows below.

Focus on:
- Correctness bugs, concurrency / thread-safety issues, and resource leaks.
- Public API and SPI stability — flag breaking changes to coco-api-core
  contracts and starter / autoconfigure wiring.
- Adherence to project rules in AGENTS.md (module boundaries, no
  common->feature dependencies, messages in bundles not hard-coded,
  generated source preferred over runtime magic).
- Security of request handling (signatures, encryption, replay protection,
  tenant / data-permission SQL isolation).
- Test coverage for the change.

Reply format (Markdown):
- Group findings by severity: ## Blockers, ## Warnings, ## Nits.
- One bullet per finding, each <= 2 lines, anchored to a file path or symbol
  when possible.
- If there are no issues, reply with a single line: NO_ISSUES
- Do not restate the diff. Do not include pleasantries.
EOF

# Build the request body with jq so JSON escaping is correct.
REQUEST_BODY="$(jq -nc \
  --arg model "${CLAUDE_MODEL}" \
  --arg prompt "${PROMPT}" \
  --arg diff "${DIFF_CONTENT}" \
  '{
     model: $model,
     max_tokens: 4096,
     messages: [
       {
         role: "user",
         content: ($prompt + "\n\n```diff\n" + $diff + "\n```")
       }
     ]
   }')"

# Call the API. Use a generous timeout: a relay on the far side of a slow link
# can take a minute for large reviews.
HTTP_RESPONSE="$(curl -fsS \
  --max-time 180 \
  -X POST "${BASE_URL}/messages" \
  -H "x-api-key: ${ANTHROPIC_API_KEY}" \
  -H "anthropic-version: 2023-06-01" \
  -H "content-type: application/json" \
  -d "${REQUEST_BODY}" \
  -w '\n%{http_code}')" || {
    echo "::error::Claude API request failed (curl exit $?)" >&2
    exit 1
  }

HTTP_CODE="$(printf '%s' "${HTTP_RESPONSE}" | tail -n1)"
BODY="$(printf '%s' "${HTTP_RESPONSE}" | sed '$d')"

if [[ "${HTTP_CODE}" != "200" ]]; then
  echo "::error::Claude API returned HTTP ${HTTP_CODE}" >&2
  # Surface the upstream error message verbatim — most relays return JSON.
  printf '%s\n' "${BODY}" >&2
  exit 1
fi

# Stop reasons other than end_turn mean we don't have a complete answer.
STOP_REASON="$(printf '%s' "${BODY}" | jq -r '.stop_reason // "unknown"')"
case "${STOP_REASON}" in
  end_turn) ;;
  refusal)
    echo "::warning::Claude declined to review (stop_reason=refusal). Posting neutral note." >&2
    printf 'NO_ISSUES\n'
    exit 0
    ;;
  max_tokens)
    echo "::warning::Claude response hit max_tokens; truncating." >&2
    ;;
  *)
    echo "::warning::Unexpected stop_reason=${STOP_REASON}; returning whatever text was produced." >&2
    ;;
esac

# Extract the first text block. Multi-block responses (e.g. refusal interleaved
# with text) are flattened to just the text portion.
REVIEW_TEXT="$(printf '%s' "${BODY}" \
  | jq -r '[.content[]? | select(.type == "text") | .text] | join("\n\n")')"

if [[ -z "${REVIEW_TEXT}" || "${REVIEW_TEXT}" == "null" ]]; then
  echo "::warning::No text content in Claude response." >&2
  printf 'NO_ISSUES\n'
  exit 0
fi

printf '%s\n' "${REVIEW_TEXT}"
