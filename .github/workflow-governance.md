# Workflow Governance

## Naming Convention

- GitHub Actions workflow files use lowercase kebab-case names.
- Reusable-only workflows use the `reusable-*.yml` prefix.
- Python automation uses snake_case modules and `test_*.py` test modules; Node.js automation uses lowercase kebab-case files.
- Stable external contracts are workflow/job context names such as `CI gate`, `Agent jury gate`, and `Agent issue gate`; file renames must not change those contexts.

## Protected Merge Path

`main` accepts changes through pull requests. The repository requires the stable
`CI gate`, `Agent jury gate`, and `Agent issue gate` contexts, requires branches
to be current with `main`, requires one current approval, resolves review
conversations before merge, and rejects force pushes or branch deletion. The
repository currently has one human collaborator, so the owner retains the
administrator emergency bypass; the documented PR path remains the normal
development flow.

Trusted Agent-review runtime changes are the narrow bootstrap exception. Because
`pull_request_target` deliberately executes the protected base version, a PR
cannot safely self-host new reviewer code with repository secrets. The owner may
use the emergency bypass only when a protected-base reviewer defect prevents the
`Agent jury gate` from succeeding and the failure is confirmed not to be a valid
P0/P1 finding. The `CI gate`, exact-head protocol tests, independent review, and
conversation resolution must also succeed. The merged base must then pass
same-repository and no-secret canaries before other normal merges. PR-head
reviewer code must never receive repository secrets.

The repository keeps merge commits enabled and disables squash and rebase
merges. Head branches are deleted automatically after merge.

## CI Gate

`.github/workflows/ci.yml` is the deterministic required-check entry point. It
joins three reusable jobs:

- cross-platform framework and sample verification;
- blocking actionlint, ShellCheck, jury protocol tests, and Checkstyle, plus a
  report-only SpotBugs baseline;
- CodeQL Java analysis.

Do not add matrix-generated names such as `Test / verify (ubuntu-latest)` to
branch protection. Their names and dimensions are implementation details; the
`CI gate` context is the stable deterministic contract. Secret-backed review
publishes the separate stable `Agent jury gate` status.

CI runs once for pull requests targeting `main`, once after a merge to `main`,
and on explicit dispatch. Feature-branch pushes do not start a duplicate full
pipeline. SpotBugs reports remain visible as artifacts but do not block until
the pre-existing `EI_EXPOSE_REP` baseline is cleared; this exception must not be
described as a clean or blocking baseline.

## Agent Review Jury

The Agent workflow uses `pull_request_target`, so workflow YAML, executable
review tooling, prompts, policy, and token permissions always come from the
protected base branch. It fetches proposed files and diffs through GitHub APIs
as untrusted text and never checks out or executes the PR head.

Only same-repository, non-bot pull requests enter the secret-backed Agent path.
Fork and bot pull requests never receive repository Agent secrets; they publish
a no-secret policy status and remain pending until a maintainer with write,
maintain, or admin permission approves the current head SHA. This path does not
write a managed PR comment because GitHub may reject comment writes on
Dependabot or fork-associated events; the approval and bound status remain the
visible audit record. Publisher jobs are serialized per pull request and
re-read the current head and approval immediately before writing status, so a
head event cannot overwrite a newer approval or dismissal result.

The secret-backed path is an actual review panel:

- five specialists independently review architecture/API, correctness,
  security/isolation, tests/release, and blind robustness;
- two verifiers independently classify every P0/P1/P2/P3 claim as `AGREE`,
  `DISAGREE`, or `UNVERIFIED`;
- one chair deduplicates and presents the result without changing severity,
  verifier votes, or the deterministic verdict.

The protected configuration caps each specialist and chair call at 4,096
output tokens and each verifier call at 8,192 output tokens so full P0-P3
coverage can fit the strict response contract. Every fresh completion or
protocol correction uses the same role-specific cap, and all attempts share a
fixed maximum of three model calls per role.

P0/P1 blocks `Agent jury gate` only when both verifiers return `AGREE`. P2/P3
never directly affect that verdict; only dual-`AGREE` P2/P3 findings enter the
chair's eligible follow-up pool, and only chair-selected entries become
actionable. P2/P3 findings with `DISAGREE` or `UNVERIFIED` from either verifier
remain visible but cannot become actionable. Consensus and eligibility use only
structured severity, finding IDs, and explicit verifier statuses, never prose,
keywords, regular expressions, `confidence`, or another text heuristic. A
max-token completion, empty text response, malformed model-output JSON, or
another explicitly retryable non-completion receives a bounded fresh
completion with the same protected prompt and bound input. Parseable reports
that pass identity and binding checks but violate the report contract receive a
protected correction. Fresh completions and corrections share the same
three-call ceiling and may occur in sequence; exhaustion fails closed. Refusal,
timeout, API or authentication failure, invalid envelopes, identity or hash
mismatch, incomplete role sets, stale PR SHA, and oversized required context
fail closed immediately. Specialist `confidence` is optional advisory metadata;
when present it remains strictly typed and bounded, but it never affects the
verdict or actionable eligibility. Every report binds to the base SHA,
head SHA, and canonical context SHA-256; that context also binds the base-version
config, prompts, and reviewer script through a protocol SHA-256. Before
publishing, the trusted publisher revalidates every role report, recomputes
consensus, and re-renders the comment.

Model-controlled text is published only after single-line normalization and
neutralization of active Markdown, mentions, issue references, and autolinks.
The detailed review body has a 40,000-byte budget. Oversized reports switch to
a deterministic compact view that still lists every finding disposition and
both verifier votes. The complete comment, including actionable Issue links and
the workflow footer, has a 64,000-byte hard limit.

The workflow publishes `Agent jury gate` and `Agent issue gate` statuses through
the built-in GitHub Actions App so every path has one stable required-check
provider. A dedicated Coco GitHub App is used only for the managed jury comment,
finding Issues, README pull requests, and final merge commit. Its publisher
token requests `Issues: write` for finding Issues and `Pull requests: write` for
comments on PR resources. It never submits a GitHub review or approval; branch
protection still requires a current human approval.

Every deterministically confirmed P0/P1 blocker and every chair-selected,
source-bound P2/P3 follow-up from the dual-`AGREE` eligible pool is reconciled to
an Issue labeled `agent-review`. Selecting the P2/P3 follow-up makes it
actionable without changing the jury verdict. The Issue carries a strict marker
binding it to the source pull request, first observed head, and stable finding
fingerprint. A later review updates, reopens, or closes that Issue. Any open
bound Issue keeps `Agent issue gate` failed, including after the pull request
head changes.

Required repository secrets:

- `ANTHROPIC_API_KEY`
- `ANTHROPIC_BASE_URL`

Optional repository variable:

- `CLAUDE_MODEL` (defaults to `claude-sonnet-4-6`)

Dedicated Agent identity configuration:

- environment `coco-agent`, restricted to the exact `main` branch;
- environment secret `COCO_AGENT_APP_PRIVATE_KEY`;
- repository variables `COCO_AGENT_APP_CLIENT_ID`, `COCO_AGENT_APP_SLUG`,
  `COCO_AGENT_APP_LOGIN`, and `COCO_AGENT_APP_BOT_ID`.

Install the App only on this repository with read/write `Contents`, `Issues`, and
`Pull requests`. GitHub supplies read-only `Metadata`; do not grant `Actions`,
`Checks`, `Commit statuses`, or `Administration` permissions.

The private key is available only to protected trusted-publisher, README
maintenance, and auto-merge jobs. Fork/bot no-secret review and the standalone
issue gate never reference it.

`Agent issue gate` remains advisory during bootstrap. Add it to `main` branch
protection only after the dedicated App and same-repository, no-secret, Issue,
and auto-merge canaries have all passed from protected `main`.

## Repository Automations

README content is maintained as paired English and Chinese fragments under
`.github/readme/`; root README files are deterministic generated outputs. A
weekly script-only pass refreshes stars and contributors. The content Agent runs
monthly or by explicit dispatch, and only after a protected baseline check finds
architecture or documentation-relevant changes since the last successful scan.
The workflow always rebuilds its automation branch from protected `main`, runs
only protected scripts while holding secrets, and opens or updates a pull
request through the dedicated App. It never pushes directly to `main` and never
executes code from a previous automation branch.

The auto-merge workflow executes the protected default-branch script and treats
events only as candidate hints. Immediately before a merge commit it rechecks
the exact head, repository merge settings, `CI gate`, `Agent jury gate`, `Agent
issue gate`, current non-bot maintainer approval, unresolved review threads, and
open bound Agent Issues. Missing or stale state exits without merging; no
administrator bypass is used. Workflow completions and bound Issue changes wake
the evaluator immediately. Approval and review-thread changes are discovered by
the ten-minute protected-`main` scan because pull-request review events use an
unprotected merge ref and therefore cannot enter the secret-bearing
`coco-agent` environment.

External Actions are pinned to immutable commit SHAs. Dependabot groups weekly
GitHub Actions updates and separately tracks the pinned Python CI dependency.

## Local Validation

Before publishing workflow changes, run the available subset locally and rely
on the PR CI gate for the hosted-runner matrix:

```powershell
go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12
python -m py_compile .github/scripts/agent_review.py .github/scripts/agent_issue_gate.py .github/scripts/auto_merge.py .github/scripts/test_agent_review.py .github/scripts/test_auto_merge.py
python .github/scripts/test_agent_review.py
python .github/scripts/test_auto_merge.py
python -m ruff check .github/scripts
python -m ruff format --check .github/scripts
node --test .github/readme/tests/readme.test.mjs
node .github/readme/scripts/render.mjs --check
mvn -B -ntp spotbugs:check
mvn -B -ntp checkstyle:check
git diff --check
```
