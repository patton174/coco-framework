import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { cp, mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  DEFAULT_REPOSITORY_ROOT,
  fragmentsForMaintainer,
  loadManifest,
  renderDocuments,
} from "../scripts/readme-lib.mjs";
import {
  replaceMarkedSection,
  updateInsights,
} from "../scripts/update-insights.mjs";
import {
  maintainContent,
  resolveAnthropicEndpoint,
  validateAgentUpdates,
} from "../scripts/maintain-content.mjs";
import { assertTrustedMain } from "../scripts/assert-trusted-main.mjs";
import { scanContentChanges } from "../scripts/scan-content-changes.mjs";

const fixturePath = fileURLToPath(new URL("./fixtures/insights.json", import.meta.url));

test("checked-in README outputs match deterministic rendering", async () => {
  const first = await renderDocuments(DEFAULT_REPOSITORY_ROOT);
  const second = await renderDocuments(DEFAULT_REPOSITORY_ROOT);
  assert.deepEqual(first, second);

  for (const [output, expected] of first) {
    assert.equal(await readFile(path.join(DEFAULT_REPOSITORY_ROOT, output), "utf8"), expected);
    assert.match(expected, /^<!-- Generated from \.github\/readme\/manifest\.json\./);
    assert.ok(expected.endsWith("\n"));
    assert.ok(!expected.includes("\r"));
  }
});

test("manifest keeps locale fragments paired and assigns one script fragment per document", async () => {
  const manifest = await loadManifest(DEFAULT_REPOSITORY_ROOT);
  const expectedIds = manifest.documents[0].fragments.map(({ id }) => id);
  for (const document of manifest.documents) {
    assert.deepEqual(document.fragments.map(({ id }) => id), expectedIds);
    assert.equal(document.fragments.filter(({ maintainer }) => maintainer === "script").length, 1);
  }
});

test("insights updater is deterministic with an offline fixture", async (context) => {
  const repositoryRoot = await createFixtureRepository();
  context.after(() => rm(repositoryRoot, { recursive: true, force: true }));

  const first = await updateInsights({
    repositoryRoot,
    repository: "patton174/coco-framework",
    date: "2026-07-11",
    fixturePath,
  });
  const firstRendered = await renderDocuments(repositoryRoot);
  const second = await updateInsights({
    repositoryRoot,
    repository: "patton174/coco-framework",
    date: "2026-07-11",
    fixturePath,
  });
  const secondRendered = await renderDocuments(repositoryRoot);

  assert.equal(first.changed.length, 2);
  assert.deepEqual(second.changed, []);
  assert.deepEqual(firstRendered, secondRendered);
  assert.match(firstRendered.get("README.md"), /<strong>1,234<\/strong><br\/>Stars/);
  assert.match(firstRendered.get("README.md"), /<sub>alice<\/sub>/);
  assert.match(firstRendered.get("README_CN.md"), /更新时间: 2026-07-11/);
  assert.doesNotMatch(firstRendered.get("README.md"), /release\[bot\]|invalid&lt;login/);
});

test("marker replacement rejects missing, misordered, and duplicate markers", () => {
  assert.throws(() => replaceMarkedSection("plain text", "COCO_STATS", "value"), /Missing or misordered/);
  assert.throws(
    () => replaceMarkedSection(
      "<!-- COCO_STATS_END -->\n<!-- COCO_STATS_START -->",
      "COCO_STATS",
      "value",
    ),
    /Missing or misordered/,
  );
  assert.throws(
    () => replaceMarkedSection(
      "<!-- COCO_STATS_START -->\n<!-- COCO_STATS_START -->\n<!-- COCO_STATS_END -->",
      "COCO_STATS",
      "value",
    ),
    /Duplicate/,
  );
});

test("Agent updates are limited to manifest-owned content fragments", async () => {
  const manifest = await loadManifest(DEFAULT_REPOSITORY_ROOT);
  const englishAgentFragments = fragmentsForMaintainer(manifest, "agent", "en");
  const allowed = new Map(englishAgentFragments.map((fragment) => [fragment.path, fragment]));
  const overview = englishAgentFragments.find(({ id }) => id === "overview");
  validateAgentUpdates({
    updates: [{
      path: overview.path,
      content: "## Overview\n\nEvidence-backed text.",
      reason: "Architecture changed.",
    }],
  }, allowed);

  assert.throws(() => validateAgentUpdates({
    updates: [{
      path: "fragments/en/80-community.md",
      content: "## Community",
      reason: "Not Agent-owned.",
    }],
  }, allowed), /unowned/);
});

test("Agent orchestration applies only analyst-recommended fragments after verification", async (context) => {
  const repositoryRoot = await createFixtureRepository();
  context.after(() => rm(repositoryRoot, { recursive: true, force: true }));
  await cp(path.join(DEFAULT_REPOSITORY_ROOT, "AGENTS.md"), path.join(repositoryRoot, "AGENTS.md"));
  await cp(path.join(DEFAULT_REPOSITORY_ROOT, "pom.xml"), path.join(repositoryRoot, "pom.xml"));
  initializeGitRepository(repositoryRoot);

  const roles = [];
  const fakeClient = {
    async completeJson({ role, payload, validate }) {
      roles.push(role);
      let value;
      if (role === "architecture analyst") {
        value = {
          needsChanges: true,
          summary: "The overview needs one evidence-backed clarification.",
          evidence: [{ path: "pom.xml", observation: "Fixture evidence." }],
          recommendations: [{ fragmentId: "overview", action: "update", reason: "Fixture update." }],
        };
      } else if (role === "zh-CN README editor") {
        const overview = payload.target.find(({ id }) => id === "overview");
        value = {
          updates: [{
            path: overview.path,
            content: `${overview.content.trim()}\n\n中文基准更新。`,
            reason: "先更新中文结构基准。",
          }],
        };
      } else if (role === "en README editor") {
        const chineseOverview = payload.counterpart.find(({ id }) => id === "overview");
        assert.match(chineseOverview.content, /中文基准更新。/);
        const overview = payload.target.find(({ id }) => id === "overview");
        value = {
          updates: [{
            path: overview.path,
            content: `${overview.content.trim()}\n\nEnglish update aligned to the Chinese baseline.`,
            reason: "Align English with the updated Chinese baseline.",
          }],
        };
      } else {
        assert.equal(role, "bilingual README verifier");
        assert.equal(payload.documents.length, 2);
        assert.equal(payload.proposedUpdates.length, 2);
        value = { decision: "accept", issues: [] };
      }
      validate(value);
      return value;
    },
  };

  const result = await maintainContent({ repositoryRoot, client: fakeClient });
  assert.deepEqual(result.changed, [
    "fragments/en/10-overview.md",
    "fragments/zh-CN/10-overview.md",
  ]);
  assert.deepEqual(roles, [
    "architecture analyst",
    "zh-CN README editor",
    "en README editor",
    "bilingual README verifier",
  ]);
  assert.match(
    await readFile(path.join(repositoryRoot, ".github/readme/fragments/zh-CN/10-overview.md"), "utf8"),
    /中文基准更新。/,
  );
  assert.match(
    await readFile(path.join(repositoryRoot, ".github/readme/fragments/en/10-overview.md"), "utf8"),
    /English update aligned to the Chinese baseline\./,
  );
});

test("old automation branch scripts are rejected before execution", async (context) => {
  const repositoryRoot = await createFixtureRepository();
  context.after(() => rm(repositoryRoot, { recursive: true, force: true }));
  initializeGitRepository(repositoryRoot);
  const protectedSha = gitOutput(repositoryRoot, ["rev-parse", "HEAD"]);
  execFileSync("git", ["update-ref", "refs/remotes/origin/main", protectedSha], { cwd: repositoryRoot });

  execFileSync("git", ["switch", "--quiet", "-c", "automation/readme-maintenance"], { cwd: repositoryRoot });
  const markerPath = path.join(repositoryRoot, "automation-script-executed.txt");
  const automationScript = path.join(repositoryRoot, ".github", "readme", "scripts", "automation-only.mjs");
  await writeFile(
    automationScript,
    `import { writeFileSync } from "node:fs";\nwriteFileSync(${JSON.stringify(markerPath)}, "executed\\n");\n`,
    "utf8",
  );
  execFileSync("git", ["add", ".github/readme/scripts/automation-only.mjs"], { cwd: repositoryRoot });
  execFileSync("git", ["commit", "--quiet", "-m", "untrusted automation script"], { cwd: repositoryRoot });

  let scriptInvocations = 0;
  assert.throws(() => {
    assertTrustedMain(repositoryRoot);
    scriptInvocations += 1;
    execFileSync(process.execPath, [automationScript], { cwd: repositoryRoot });
  }, /outside origin\/main/);
  assert.equal(scriptInvocations, 0);
  await assert.rejects(readFile(markerPath, "utf8"), { code: "ENOENT" });

  execFileSync("git", ["switch", "--quiet", "--detach", "refs/remotes/origin/main"], { cwd: repositoryRoot });
  assert.equal(assertTrustedMain(repositoryRoot), protectedSha);
  await assert.rejects(readFile(automationScript, "utf8"), { code: "ENOENT" });
});

test("scheduled scan with no relevant changes performs zero model calls and advances baseline", async (context) => {
  const repositoryRoot = await createFixtureRepository();
  context.after(() => rm(repositoryRoot, { recursive: true, force: true }));
  initializeGitRepository(repositoryRoot);
  const baselineSha = gitOutput(repositoryRoot, ["rev-parse", "HEAD"]);
  const statePath = path.join(repositoryRoot, ".github", "readme", "state", "content-baseline.json");
  await writeFile(statePath, `${JSON.stringify({
    schemaVersion: 1,
    lastScannedCommit: baselineSha,
    lastRelevantPaths: [],
  }, null, 2)}\n`, "utf8");
  await mkdir(path.join(repositoryRoot, "misc"), { recursive: true });
  await writeFile(path.join(repositoryRoot, "misc", "notes.txt"), "not README architecture input\n", "utf8");
  execFileSync("git", ["add", ".github/readme/state/content-baseline.json", "misc/notes.txt"], {
    cwd: repositoryRoot,
  });
  execFileSync("git", ["commit", "--quiet", "-m", "irrelevant change"], { cwd: repositoryRoot });
  const headSha = gitOutput(repositoryRoot, ["rev-parse", "HEAD"]);

  const scan = await scanContentChanges({ repositoryRoot, head: "HEAD", force: false });
  let modelCalls = 0;
  const fakeAnthropicClient = {
    async completeJson() {
      modelCalls += 1;
      throw new Error("The model must not be called for an irrelevant scheduled scan.");
    },
  };
  if (scan.invokeAgent) {
    await maintainContent({ repositoryRoot, client: fakeAnthropicClient });
  }

  assert.deepEqual(scan.relevantPaths, []);
  assert.equal(scan.invokeAgent, false);
  assert.equal(modelCalls, 0);
  assert.deepEqual(JSON.parse(await readFile(statePath, "utf8")), {
    schemaVersion: 1,
    lastScannedCommit: headSha,
    lastRelevantPaths: [],
  });

  const forced = await scanContentChanges({ repositoryRoot, head: "HEAD", force: true });
  assert.equal(forced.invokeAgent, true);
});

test("Anthropic base URL is restricted to an HTTPS origin or v1 endpoint", () => {
  assert.equal(resolveAnthropicEndpoint("https://api.anthropic.com"), "https://api.anthropic.com/v1/messages");
  assert.equal(resolveAnthropicEndpoint("https://proxy.example/v1/"), "https://proxy.example/v1/messages");
  assert.throws(() => resolveAnthropicEndpoint("http://api.anthropic.com"), /HTTPS/);
  assert.throws(() => resolveAnthropicEndpoint("https://proxy.example/custom"), /empty or \/v1/);
});

test("workflow uses App-authenticated PR maintenance without push or auto-merge", async () => {
  const workflow = await readFile(
    path.join(DEFAULT_REPOSITORY_ROOT, ".github", "workflows", "readme-maintenance.yml"),
    "utf8",
  );
  assert.match(workflow, /client-id:\s*\$\{\{ vars\.COCO_AGENT_APP_CLIENT_ID \}\}/);
  assert.match(workflow, /secrets\.COCO_AGENT_APP_PRIVATE_KEY/);
  assert.match(workflow, /vars\.COCO_AGENT_APP_SLUG/);
  assert.match(workflow, /vars\.COCO_AGENT_APP_LOGIN/);
  assert.match(workflow, /vars\.COCO_AGENT_APP_BOT_ID/);
  assert.match(workflow, /ACTUAL_APP_SLUG.*EXPECTED_APP_SLUG/);
  assert.match(workflow, /actual_bot_id.*EXPECTED_APP_BOT_ID/);
  assert.match(workflow, /secrets\.ANTHROPIC_API_KEY/);
  assert.match(workflow.slice(workflow.indexOf("  maintain:")), /^    environment: coco-agent$/m);
  assert.match(workflow, /group: maintain-readme-automation-\$\{\{ github\.repository_id \}\}/);
  assert.match(workflow, /head=\$\{REPOSITORY_OWNER\}:\$\{BRANCH\}/);
  assert.match(workflow, /\.user\.login/);
  assert.match(workflow, /\.user\.id/);
  assert.match(workflow, /\.head\.repo\.full_name/);
  assert.match(workflow, /gh api --method POST "repos\/\$\{GITHUB_REPOSITORY\}\/pulls"/);
  assert.doesNotMatch(workflow, /^\s*app-id:/m);
  assert.doesNotMatch(workflow, /COCO_AGENT_APP_ID/);
  assert.doesNotMatch(workflow, /gh pr create/);
  assert.doesNotMatch(workflow, /^\s*push:/m);
  assert.doesNotMatch(workflow, /git push[^\n]*\bmain\b/);
  assert.doesNotMatch(workflow, /gh pr merge/);
});

test("secret-bearing maintenance executes only detached origin/main scripts", async () => {
  const workflow = await readFile(
    path.join(DEFAULT_REPOSITORY_ROOT, ".github", "workflows", "readme-maintenance.yml"),
    "utf8",
  );
  const maintainJob = workflow.slice(workflow.indexOf("  maintain:"));
  const pinIndex = maintainJob.indexOf("- name: Pin protected execution tree");
  const firstRepositoryScriptIndex = maintainJob.indexOf("node .github/readme/scripts/");
  const commitIndex = maintainJob.indexOf("- name: Commit scoped README updates from protected base");
  const automationBranchIndex = maintainJob.indexOf("automation/readme-maintenance");

  assert.ok(pinIndex >= 0 && pinIndex < firstRepositoryScriptIndex);
  assert.ok(firstRepositoryScriptIndex < commitIndex);
  assert.ok(automationBranchIndex > commitIndex);
  assert.match(maintainJob, /git fetch --no-tags origin "\+refs\/heads\/main:refs\/remotes\/origin\/main"/);
  assert.match(maintainJob, /git switch --detach refs\/remotes\/origin\/main/);
  assert.equal((maintainJob.match(/git switch/g) || []).length, 1);
  assert.doesNotMatch(maintainJob, /git fetch[^\n]*(?:automation\/readme-maintenance|\$\{BRANCH\})/);
  assert.doesNotMatch(maintainJob, /refs\/remotes\/origin\/\$\{BRANCH\}/);
  assert.match(maintainJob, /git ls-remote --refs origin "refs\/heads\/\$\{BRANCH\}"/);
  assert.match(maintainJob, /--force-with-lease=refs\/heads\/\$\{BRANCH\}:\$\{remote_branch_sha\}/);
  assert.match(
    maintainJob,
    /if: steps\.mode\.outputs\.run-content == 'true' && steps\.content-scan\.outputs\.invoke-agent == 'true'/,
  );
  assert.ok(
    maintainJob.indexOf("node .github/readme/scripts/assert-trusted-main.mjs")
      < maintainJob.indexOf("node .github/readme/scripts/maintain-content.mjs"),
  );
});

async function createFixtureRepository() {
  const repositoryRoot = await mkdtemp(path.join(os.tmpdir(), "coco-readme-test-"));
  await mkdir(path.join(repositoryRoot, ".github"), { recursive: true });
  await cp(
    path.join(DEFAULT_REPOSITORY_ROOT, ".github", "readme"),
    path.join(repositoryRoot, ".github", "readme"),
    { recursive: true },
  );
  await writeFile(path.join(repositoryRoot, "README.md"), "fixture\n", "utf8");
  await writeFile(path.join(repositoryRoot, "README_CN.md"), "fixture\n", "utf8");
  return repositoryRoot;
}

function initializeGitRepository(repositoryRoot) {
  execFileSync("git", ["init", "--quiet"], { cwd: repositoryRoot });
  execFileSync("git", ["config", "user.name", "README Test"], { cwd: repositoryRoot });
  execFileSync("git", ["config", "user.email", "readme-test@example.invalid"], { cwd: repositoryRoot });
  execFileSync("git", ["config", "core.autocrlf", "false"], { cwd: repositoryRoot });
  execFileSync("git", ["add", "."], { cwd: repositoryRoot });
  execFileSync("git", ["commit", "--quiet", "-m", "fixture"], { cwd: repositoryRoot });
}

function gitOutput(repositoryRoot, args) {
  return execFileSync("git", args, { cwd: repositoryRoot, encoding: "utf8" }).trim();
}
