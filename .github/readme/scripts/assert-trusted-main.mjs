import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";

import { DEFAULT_REPOSITORY_ROOT } from "./readme-lib.mjs";

export function assertTrustedMain(repositoryRoot = DEFAULT_REPOSITORY_ROOT) {
  const head = gitOutput(repositoryRoot, ["rev-parse", "HEAD"]);
  const originMain = gitOutput(repositoryRoot, ["rev-parse", "refs/remotes/origin/main"]);
  if (head !== originMain) {
    throw new Error(`Refusing to execute README maintenance outside origin/main: HEAD=${head}, origin/main=${originMain}.`);
  }

  let symbolicHead = "";
  try {
    symbolicHead = gitOutput(repositoryRoot, ["symbolic-ref", "--quiet", "--short", "HEAD"]);
  } catch (error) {
    if (error.status !== 1) {
      throw error;
    }
  }
  if (symbolicHead) {
    throw new Error(`README maintenance must execute from detached origin/main, not branch ${symbolicHead}.`);
  }
  return originMain;
}

function gitOutput(repositoryRoot, args) {
  return execFileSync("git", args, {
    cwd: repositoryRoot,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  }).trim();
}

export function main() {
  const sha = assertTrustedMain();
  process.stdout.write(`Trusted README execution tree: ${sha}.\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    main();
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  }
}
