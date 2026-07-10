#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REVIEW_SCRIPT="${SCRIPT_DIR}/claude-review.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "${TEST_ROOT}"' EXIT

mkdir -p "${TEST_ROOT}/bin"
cat > "${TEST_ROOT}/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

output_file=''
while (( $# > 0 )); do
  case "$1" in
    --output)
      output_file="$2"
      shift 2
      ;;
    --write-out | --max-time | --max-filesize | -X | -H | --data-binary)
      shift 2
      ;;
    -sS)
      shift
      ;;
    *)
      shift
      ;;
  esac
done

if [[ -z "${output_file}" ]]; then
  echo 'fake curl did not receive --output' >&2
  exit 2
fi
cp -- "${FAKE_RESPONSE_FILE}" "${output_file}"
printf '%s' "${FAKE_HTTP_CODE:-200}"
exit "${FAKE_CURL_EXIT:-0}"
EOF
chmod +x "${TEST_ROOT}/bin/curl"

write_response() {
  local file="$1"
  local stop_reason="$2"
  local text="$3"
  jq -cn --arg stop_reason "${stop_reason}" --arg text "${text}" \
    '{stop_reason: $stop_reason, content: [{type: "text", text: $text}]}' > "${file}"
}

run_case() {
  local name="$1"
  local expected_status="$2"
  local response_file="$3"
  local diff_file="$4"
  local expected_first_line="${5:-}"
  local stdout_file="${TEST_ROOT}/${name}.stdout"
  local stderr_file="${TEST_ROOT}/${name}.stderr"

  set +e
  PATH="${TEST_ROOT}/bin:${PATH}" \
    FAKE_RESPONSE_FILE="${response_file}" \
    ANTHROPIC_API_KEY='test-key' \
    ANTHROPIC_BASE_URL='https://example.invalid' \
    CLAUDE_MODEL='test-model' \
    bash "${REVIEW_SCRIPT}" "${diff_file}" > "${stdout_file}" 2> "${stderr_file}"
  local actual_status=$?
  set -e

  if [[ "${expected_status}" == 'success' && ${actual_status} -ne 0 ]]; then
    echo "FAIL ${name}: expected success, got ${actual_status}" >&2
    cat "${stderr_file}" >&2
    exit 1
  fi
  if [[ "${expected_status}" == 'failure' && ${actual_status} -eq 0 ]]; then
    echo "FAIL ${name}: expected failure" >&2
    cat "${stdout_file}" >&2
    exit 1
  fi
  if [[ -n "${expected_first_line}" ]]; then
    local actual_first_line
    actual_first_line="$(sed -n '1p' "${stdout_file}")"
    if [[ "${actual_first_line}" != "${expected_first_line}" ]]; then
      echo "FAIL ${name}: expected '${expected_first_line}', got '${actual_first_line}'" >&2
      exit 1
    fi
  fi
  echo "PASS ${name}"
}

diff_file="${TEST_ROOT}/pr.diff"
printf 'diff --git a/A.java b/A.java\n+new line\n' > "${diff_file}"

pass_response="${TEST_ROOT}/pass.json"
write_response "${pass_response}" 'end_turn' $'VERDICT: PASS\nNo findings.'
run_case 'pass-verdict' 'success' "${pass_response}" "${diff_file}" 'VERDICT: PASS'

block_response="${TEST_ROOT}/block.json"
write_response "${block_response}" 'end_turn' $'VERDICT: BLOCK\n\n## Blockers\n- Fix the issue.'
run_case 'block-verdict-is-valid-output' 'success' "${block_response}" "${diff_file}" 'VERDICT: BLOCK'

invalid_response="${TEST_ROOT}/invalid.json"
write_response "${invalid_response}" 'end_turn' 'No findings.'
run_case 'missing-verdict' 'failure' "${invalid_response}" "${diff_file}"

max_tokens_response="${TEST_ROOT}/max-tokens.json"
write_response "${max_tokens_response}" 'max_tokens' $'VERDICT: PASS\nPartial review.'
run_case 'max-tokens' 'failure' "${max_tokens_response}" "${diff_file}"

refusal_response="${TEST_ROOT}/refusal.json"
write_response "${refusal_response}" 'refusal' $'VERDICT: PASS\nNo findings.'
run_case 'refusal' 'failure' "${refusal_response}" "${diff_file}"

oversized_diff="${TEST_ROOT}/oversized.diff"
awk 'BEGIN { for (i = 0; i < 60001; i++) printf "x" }' > "${oversized_diff}"
run_case 'oversized-diff' 'failure' "${pass_response}" "${oversized_diff}"

echo 'All Claude review script tests passed.'
