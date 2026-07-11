import { execFileSync } from "node:child_process";
import { appendFile, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

import { assertTrustedMain } from "./assert-trusted-main.mjs";
import {
  DEFAULT_REPOSITORY_ROOT,
  README_DIRECTORY,
  resolveWithin,
} from "./readme-lib.mjs";

const DEFAULT_RULES_PATH = path.join(README_DIRECTORY, "content-scan.json");
const DEFAULT_STATE_PATH = path.join(README_DIRECTORY, "state", "content-baseline.json");

export async function scanContentChanges({
  repositoryRoot = DEFAULT_REPOSITORY_ROOT,
  head = "HEAD",
  force = false,
  rulesPath = DEFAULT_RULES_PATH,
  statePath = DEFAULT_STATE_PATH,
} = {}) {
  const rulesFile = resolveWithin(repositoryRoot, rulesPath, "README content scan rules");
  const stateFile = resolveWithin(repositoryRoot, statePath, "README content scan state");
  const rules = JSON.parse(await readFile(rulesFile, "utf8"));
  const state = JSON.parse(await readFile(stateFile, "utf8"));
  validateRules(rules);
  validateState(state);

  const baselineSha = resolveCommit(repositoryRoot, state.lastScannedCommit);
  const headSha = resolveCommit(repositoryRoot, head);
  assertAncestor(repositoryRoot, baselineSha, headSha);
  const changedPaths = changedPathsBetween(repositoryRoot, baselineSha, headSha);
  const relevantPaths = changedPaths.filter((changedPath) => isRelevantPath(changedPath, rules));
  const invokeAgent = Boolean(force) || relevantPaths.length > 0;

  const nextState = {
    schemaVersion: 1,
    lastScannedCommit: headSha,
    lastRelevantPaths: relevantPaths,
  };
  await writeFile(stateFile, `${JSON.stringify(nextState, null, 2)}\n`, "utf8");

  return {
    baselineSha,
    headSha,
    changedPaths,
    relevantPaths,
    force: Boolean(force),
    invokeAgent,
  };
}

export function isRelevantPath(candidate, rules) {
  const normalized = normalizeGitPath(candidate);
  if (matchesPathSet(normalized, rules.exclude)) {
    return false;
  }
  return matchesPathSet(normalized, rules.include);
}

export function validateRules(rules) {
  if (!rules || rules.schemaVersion !== 1) {
    throw new Error("README content scan rules schemaVersion must be 1.");
  }
  validatePathSet(rules.include, "include");
  validatePathSet(rules.exclude, "exclude");
}

export function validateState(state) {
  if (!state || state.schemaVersion !== 1) {
    throw new Error("README content scan state schemaVersion must be 1.");
  }
  if (!/^[0-9a-f]{40}$/.test(state.lastScannedCommit)) {
    throw new Error("README content scan state must contain a full lowercase commit SHA.");
  }
  if (!Array.isArray(state.lastRelevantPaths) || state.lastRelevantPaths.some((entry) => typeof entry !== "string")) {
    throw new Error("README content scan state lastRelevantPaths must be a string array.");
  }
}

function validatePathSet(pathSet, label) {
  if (!pathSet || typeof pathSet !== "object" || Array.isArray(pathSet)) {
    throw new Error(`README content scan ${label} rules must be an object.`);
  }
  const actualKeys = Object.keys(pathSet).sort();
  const expectedKeys = ["exact", "prefixes", "suffixes"];
  if (JSON.stringify(actualKeys) !== JSON.stringify(expectedKeys)) {
    throw new Error(`README content scan ${label} rules must define exact, prefixes, and suffixes.`);
  }
  for (const key of expectedKeys) {
    if (!Array.isArray(pathSet[key]) || pathSet[key].some((entry) => typeof entry !== "string" || !entry)) {
      throw new Error(`README content scan ${label}.${key} must be a non-empty string array.`);
    }
  }
}

function matchesPathSet(candidate, pathSet) {
  return pathSet.exact.includes(candidate)
    || pathSet.prefixes.some((prefix) => candidate.startsWith(prefix))
    || pathSet.suffixes.some((suffix) => candidate.endsWith(suffix));
}

function normalizeGitPath(candidate) {
  if (typeof candidate !== "string" || !candidate || candidate.includes("\0")) {
    throw new Error("Git changed paths must be non-empty strings without NUL bytes.");
  }
  const normalized = candidate.replaceAll("\\", "/");
  if (normalized.startsWith("/") || normalized.split("/").includes("..")) {
    throw new Error(`Git changed path escapes the repository: ${candidate}`);
  }
  return normalized;
}

function resolveCommit(repositoryRoot, reference) {
  const sha = execFileSync("git", ["rev-parse", "--verify", `${reference}^{commit}`], {
    cwd: repositoryRoot,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  }).trim();
  if (!/^[0-9a-f]{40}$/.test(sha)) {
    throw new Error(`Unable to resolve a full commit SHA for ${reference}.`);
  }
  return sha;
}

function assertAncestor(repositoryRoot, baselineSha, headSha) {
  try {
    execFileSync("git", ["merge-base", "--is-ancestor", baselineSha, headSha], {
      cwd: repositoryRoot,
      stdio: "ignore",
    });
  } catch (error) {
    throw new Error(`README content baseline ${baselineSha} is not an ancestor of ${headSha}.`);
  }
}

function changedPathsBetween(repositoryRoot, baselineSha, headSha) {
  const output = execFileSync(
    "git",
    ["diff", "--name-only", "--diff-filter=ACDMRTUXB", "-z", baselineSha, headSha, "--"],
    {
      cwd: repositoryRoot,
      encoding: "buffer",
      maxBuffer: 4 * 1024 * 1024,
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  return [...new Set(output.toString("utf8").split("\0").filter(Boolean).map(normalizeGitPath))].sort();
}

function parseArguments(args) {
  const options = { head: "HEAD", force: false, githubOutput: "" };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (!["--head", "--force", "--github-output"].includes(argument) || args[index + 1] === undefined) {
      throw new Error(
        "Usage: node .github/readme/scripts/scan-content-changes.mjs [--head ref] [--force true|false] [--github-output path]",
      );
    }
    const value = args[index + 1];
    if (argument === "--force") {
      if (!["true", "false"].includes(value)) {
        throw new Error("--force must be true or false.");
      }
      options.force = value === "true";
    } else {
      options[argument === "--github-output" ? "githubOutput" : "head"] = value;
    }
    index += 1;
  }
  return options;
}

async function writeGitHubOutput(outputFile, result) {
  if (!outputFile) {
    return;
  }
  const output = [
    `baseline-sha=${result.baselineSha}`,
    `head-sha=${result.headSha}`,
    `relevant=${result.relevantPaths.length > 0}`,
    `invoke-agent=${result.invokeAgent}`,
    `changed-paths-json=${JSON.stringify(result.changedPaths)}`,
    `relevant-paths-json=${JSON.stringify(result.relevantPaths)}`,
  ].join("\n");
  await appendFile(outputFile, `${output}\n`, "utf8");
}

export async function main(args = process.argv.slice(2)) {
  const options = parseArguments(args);
  assertTrustedMain();
  const result = await scanContentChanges({
    head: options.head,
    force: options.force,
  });
  await writeGitHubOutput(options.githubOutput, result);
  process.stdout.write(
    `README content scan ${result.baselineSha}..${result.headSha}: ${result.relevantPaths.length} relevant path(s), invoke-agent=${result.invokeAgent}.\n`,
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
