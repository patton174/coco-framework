# README maintenance protocol

The root `README.md` and `README_CN.md` files are generated artifacts. Their source of truth is `manifest.json` plus the ordered files under `fragments/`.

## Ownership

Each manifest fragment has one writer:

- `agent`: the low-frequency architecture analyst, locale editors, and bilingual verifier in `scripts/maintain-content.mjs`.
- `script`: the GitHub API statistics updater in `scripts/update-insights.mjs`.

The Agent orchestrator validates proposed paths against the manifest and cannot write `script` fragments. The insights updater requires exactly one `script` fragment per locale and only replaces the `COCO_STATS` and `COCO_CONTRIBUTORS` marker bodies. Humans may edit source fragments in a normal pull request, but must regenerate both root documents in the same change.

## Deterministic rendering

The renderer normalizes fragment line endings, trims fragment-edge whitespace, joins fragments with one blank line, and writes one final newline. It performs no network access and does not insert timestamps.

```bash
node .github/readme/scripts/render.mjs --write
node .github/readme/scripts/render.mjs --check
node --test .github/readme/tests/readme.test.mjs
```

`--check` exits non-zero when either root README differs from its manifest and fragments.

## Automation

`.github/workflows/readme-maintenance.yml` has three entry paths:

- A weekly schedule runs only the deterministic GitHub insights updater.
- A monthly schedule runs only the Agent content pipeline.
- Manual dispatch selects `insights`, `content`, or `all`, with an optional content focus.

Pull requests that touch the README maintenance surface run offline tests and drift detection only. They do not receive App or Anthropic secrets.

Scheduled and manual maintenance run in the `coco-agent` environment, whose deployment policy accepts only the exact `main` branch, and mint one installation token through the v3 `client-id` interface using `vars.COCO_AGENT_APP_CLIENT_ID` and `secrets.COCO_AGENT_APP_PRIVATE_KEY`. Before any commit, the returned App slug and the GitHub Bot login, type, and immutable user ID must match `COCO_AGENT_APP_SLUG`, `COCO_AGENT_APP_LOGIN`, and `COCO_AGENT_APP_BOT_ID`. The App token is used for the automation branch push and pull request creation; ordinary identity reads use `github.token`. An existing maintenance PR is reused only when its author login and immutable Bot ID, source repository, source branch, and base branch all match the protected contract. Content maintenance uses the existing `ANTHROPIC_API_KEY`, `ANTHROPIC_BASE_URL`, and `CLAUDE_MODEL` configuration. Automation never pushes to `main`, never auto-merges, and does not run on `push`.

The secret-bearing job fetches `main`, detaches at `refs/remotes/origin/main`, and runs `scripts/assert-trusted-main.mjs` before any repository script or authenticated API operation. It never fetches, checks out, switches to, rebases, or executes the existing `automation/readme-maintenance` branch. After all local checks pass, the detached protected-base commit is extended with one scoped commit. The old remote branch contributes only its ref SHA to an explicit `--force-with-lease`; its files are never read.

## Content scan state

`state/content-baseline.json` is tracked on protected `main` and advances only through the same README maintenance pull request. `scripts/scan-content-changes.mjs` compares that baseline with the protected execution commit, filters changed paths through `content-scan.json`, records the completed scan, and emits the model-invocation decision.

The monthly schedule invokes the content Agents only when the filtered path set is non-empty. Generated root READMEs and `.github/readme/` maintenance files are excluded so a maintenance PR does not trigger itself next month. A manual `content`/`all` dispatch or non-empty focus explicitly forces content maintenance even when the filtered set is empty. If the scan succeeds but no model is needed, the state-only update still goes through a pull request whenever the baseline SHA changed.

## Agent contract

The content pipeline has three bounded phases:

1. An architecture analyst compares the protected project guide, root POM, recent history, and current fragments.
2. The Chinese editor updates the structural baseline first; the English editor then receives that exact in-memory Chinese proposal and returns aligned content for changed `agent` fragments.
3. A bilingual verifier accepts or rejects the complete proposal before any file is written.

All model responses must be strict JSON. A malformed response receives one shape-repair attempt. Paths, response sizes, fragment structure, ownership, and the generated result are validated locally before source fragments are changed.
