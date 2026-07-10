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
  local expected_stderr="${6:-}"
  local forbidden_output="${7:-}"
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
  if [[ "${expected_status}" == 'failure' && -s "${stdout_file}" ]]; then
    echo "FAIL ${name}: failure path produced stdout" >&2
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
  if [[ -n "${expected_stderr}" ]] \
     && ! grep -F -- "${expected_stderr}" "${stderr_file}" >/dev/null; then
    echo "FAIL ${name}: expected stderr to contain '${expected_stderr}'" >&2
    cat "${stderr_file}" >&2
    exit 1
  fi
  if [[ -n "${forbidden_output}" ]] \
     && grep -F -- "${forbidden_output}" "${stdout_file}" "${stderr_file}" >/dev/null; then
    echo "FAIL ${name}: output leaked '${forbidden_output}'" >&2
    exit 1
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

http_error_response="${TEST_ROOT}/http-error.json"
printf '{"error":{"message":"DO_NOT_ECHO_RELAY_BODY"}}\n' > "${http_error_response}"
FAKE_HTTP_CODE=500 run_case \
  'http-500' 'failure' "${http_error_response}" "${diff_file}" '' \
  '::error::Claude API returned HTTP 500.' 'DO_NOT_ECHO_RELAY_BODY'

FAKE_CURL_EXIT=7 run_case \
  'curl-transport-error' 'failure' "${pass_response}" "${diff_file}" '' \
  '::error::Claude API request failed (curl exit 7).'

invalid_structure_response="${TEST_ROOT}/invalid-structure.json"
printf '{"stop_reason":"end_turn","content":"not-an-array"}\n' > "${invalid_structure_response}"
run_case \
  'invalid-response-structure' 'failure' "${invalid_structure_response}" "${diff_file}" '' \
  '::error::Claude API returned an invalid response body.'

contradictory_response="${TEST_ROOT}/contradictory.json"
write_response "${contradictory_response}" 'end_turn' $'VERDICT: PASS\n\n## Blockers\n- Hidden blocker.'
run_case \
  'pass-with-blockers' 'failure' "${contradictory_response}" "${diff_file}" '' \
  '::error::Claude returned PASS with a Blockers section.'

empty_block_response="${TEST_ROOT}/empty-block.json"
write_response "${empty_block_response}" 'end_turn' $'VERDICT: BLOCK\n\n## Warnings\n- No blocker supplied.'
run_case \
  'block-without-blockers' 'failure' "${empty_block_response}" "${diff_file}" '' \
  '::error::Claude returned BLOCK without one populated Blockers section.'

empty_block_section_response="${TEST_ROOT}/empty-block-section.json"
write_response "${empty_block_section_response}" 'end_turn' $'VERDICT: BLOCK\n\n## Blockers\n\n## Warnings\n- Empty blocker section.'
run_case \
  'block-with-empty-blocker-section' 'failure' "${empty_block_section_response}" "${diff_file}" '' \
  '::error::Claude returned BLOCK without one populated Blockers section.'

invalid_response="${TEST_ROOT}/invalid.json"
write_response "${invalid_response}" 'end_turn' 'No findings.'
run_case \
  'missing-verdict' 'failure' "${invalid_response}" "${diff_file}" '' \
  '::error::Claude response has an invalid verdict line.'

max_tokens_response="${TEST_ROOT}/max-tokens.json"
write_response "${max_tokens_response}" 'max_tokens' $'VERDICT: PASS\nPartial review.'
run_case \
  'max-tokens' 'failure' "${max_tokens_response}" "${diff_file}" '' \
  '::error::Claude review exceeded the response token limit.'

refusal_response="${TEST_ROOT}/refusal.json"
write_response "${refusal_response}" 'refusal' $'VERDICT: PASS\nNo findings.'
run_case \
  'refusal' 'failure' "${refusal_response}" "${diff_file}" '' \
  '::error::Claude refused the review (stop_reason=refusal).'

oversized_diff="${TEST_ROOT}/oversized.diff"
awk 'BEGIN { for (i = 0; i < 60001; i++) printf "x" }' > "${oversized_diff}"
run_case \
  'oversized-diff' 'failure' "${pass_response}" "${oversized_diff}" '' \
  'Split the PR into smaller changes and retry.'

echo 'All Claude review script tests passed.'
