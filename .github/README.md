# Repository Workflow Governance

## Protected Merge Path

`main` accepts changes through pull requests. The repository requires the stable
`CI gate` and `Agent jury gate` contexts, requires branches to be current with
`main`, requires one current approval, resolves review conversations before
merge, and rejects force pushes or branch deletion. The repository currently has
one human collaborator, so the owner retains the administrator emergency bypass;
the documented PR path remains the normal development flow.

The repository keeps merge commits enabled and disables squash and rebase
merges. Head branches are deleted automatically after merge.

## CI Gate

`.github/workflows/ci.yml` is the deterministic required-check entry point. It
joins three reusable jobs:

- cross-platform framework and sample verification;
- actionlint, ShellCheck, SpotBugs, and Checkstyle;
- CodeQL Java analysis.

Do not add matrix-generated names such as `Test / verify (ubuntu-latest)` to
branch protection. Their names and dimensions are implementation details; the
`CI gate` context is the stable deterministic contract. Secret-backed review
publishes the separate stable `Agent jury gate` status.

CI runs once for pull requests targeting `main`, once after a merge to `main`,
and on explicit dispatch. Feature-branch pushes do not start a duplicate full
pipeline.

## Agent Review Jury

The Agent workflow uses `pull_request_target`, so workflow YAML, executable
review tooling, prompts, policy, and token permissions always come from the
protected base branch. It fetches proposed files and diffs through GitHub APIs
as untrusted text and never checks out or executes the PR head.

Only same-repository, non-bot pull requests enter the secret-backed Agent path.
Fork and bot pull requests never receive repository Agent secrets; they publish
a no-secret policy status and remain pending until a maintainer with write,
maintain, or admin permission approves the current head SHA.

The secret-backed path is an actual review panel:

- five specialists independently review architecture/API, correctness,
  security/isolation, tests/release, and blind robustness;
- two verifiers independently classify every P0/P1 claim as `AGREE`,
  `DISAGREE`, or `UNVERIFIED`;
- one chair deduplicates and presents the result without changing severity,
  verifier votes, or the deterministic verdict.

P0/P1 blocks only when both verifiers return `AGREE`. P2/P3 never directly
block. Refusal, timeout, malformed or schema-incompatible JSON, incomplete role
sets, hash mismatch, stale PR SHA, API failure, and oversized required context
fail closed. Every report binds to the base SHA, head SHA, and canonical context
SHA-256; that context also binds the base-version config, prompts, and reviewer
script through a protocol SHA-256. Before publishing, the trusted publisher
revalidates every role report, recomputes consensus, and re-renders the comment.
The workflow publishes the `Agent jury gate` status directly to that PR head. It
never submits a GitHub review or approval; branch protection still requires a
current human approval.

Required repository secrets:

- `ANTHROPIC_API_KEY`
- `ANTHROPIC_BASE_URL`

Optional repository variable:

- `CLAUDE_MODEL` (defaults to `claude-sonnet-4-6`)

## Repository Automations

The README insights workflow updates an automation branch, opens or updates a
pull request, explicitly dispatches `ci.yml` and a head-SHA-bound base-context
Agent review, then enables auto-merge. Its bot-authored PR takes the no-secret
Agent path, so auto-merge waits for maintainer approval. Explicit dispatch is
required because events created by the built-in `GITHUB_TOKEN` do not trigger
another workflow automatically, and protected `main` does not accept direct
automation pushes.

External Actions are pinned to immutable commit SHAs. Dependabot groups weekly
GitHub Actions updates and separately tracks the pinned Python CI dependency.

## Local Validation

Before publishing workflow changes, run the available subset locally and rely
on the PR CI gate for the hosted-runner matrix:

```powershell
go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12
python -m py_compile .github/scripts/agent_review.py .github/scripts/test_agent_review.py
python .github/scripts/test_agent_review.py
mvn -B -ntp spotbugs:check
mvn -B -ntp checkstyle:check
git diff --check
```
