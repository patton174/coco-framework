import { pathToFileURL } from "node:url";

import {
  DEFAULT_REPOSITORY_ROOT,
  writeRenderedDocuments,
} from "./readme-lib.mjs";

export async function main(args = process.argv.slice(2)) {
  const unknown = args.filter((argument) => !["--check", "--write"].includes(argument));
  if (unknown.length > 0 || (args.includes("--check") && args.includes("--write"))) {
    throw new Error("Usage: node .github/readme/scripts/render.mjs [--check|--write]");
  }
  const check = args.includes("--check");
  const result = await writeRenderedDocuments({
    repositoryRoot: DEFAULT_REPOSITORY_ROOT,
    check,
  });

  if (check && result.drifted.length > 0) {
    throw new Error(
      `Generated README drift detected: ${result.drifted.join(", ")}. Run the renderer with --write.`,
    );
  }
  const files = check ? [] : result.changed;
  process.stdout.write(
    files.length > 0
      ? `Rendered ${files.join(", ")}.\n`
      : `README outputs are ${check ? "in sync" : "already up to date"}.\n`,
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
