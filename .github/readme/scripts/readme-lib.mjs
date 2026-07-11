import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const DEFAULT_REPOSITORY_ROOT = path.resolve(
  fileURLToPath(new URL("../../../", import.meta.url)),
);

export const README_DIRECTORY = path.join(".github", "readme");
export const MANIFEST_PATH = path.join(README_DIRECTORY, "manifest.json");
export const ALLOWED_MAINTAINERS = new Set(["agent", "script"]);

export async function loadManifest(repositoryRoot = DEFAULT_REPOSITORY_ROOT) {
  const manifestFile = path.join(repositoryRoot, MANIFEST_PATH);
  let manifest;
  try {
    manifest = JSON.parse(await readFile(manifestFile, "utf8"));
  } catch (error) {
    throw new Error(`Unable to read README manifest at ${manifestFile}: ${error.message}`);
  }
  validateManifest(manifest, repositoryRoot);
  return manifest;
}

export function validateManifest(manifest, repositoryRoot = DEFAULT_REPOSITORY_ROOT) {
  if (!manifest || manifest.schemaVersion !== 1) {
    throw new Error("README manifest schemaVersion must be 1.");
  }
  if (typeof manifest.generatedNotice !== "string" || !manifest.generatedNotice.startsWith("<!--")) {
    throw new Error("README manifest generatedNotice must be an HTML comment.");
  }
  if (!Array.isArray(manifest.documents) || manifest.documents.length !== 2) {
    throw new Error("README manifest must define exactly two documents.");
  }

  const documentIds = new Set();
  const outputs = new Set();
  let pairedFragmentIds;

  for (const document of manifest.documents) {
    if (!document || typeof document.id !== "string" || documentIds.has(document.id)) {
      throw new Error("README document ids must be unique, non-empty strings.");
    }
    documentIds.add(document.id);
    if (typeof document.locale !== "string" || !document.locale) {
      throw new Error(`README document ${document.id} must define a locale.`);
    }
    if (!["README.md", "README_CN.md"].includes(document.output) || outputs.has(document.output)) {
      throw new Error(`README document ${document.id} has an invalid or duplicate output.`);
    }
    outputs.add(document.output);
    resolveWithin(repositoryRoot, document.output, `output for ${document.id}`);

    if (!Array.isArray(document.fragments) || document.fragments.length === 0) {
      throw new Error(`README document ${document.id} must define fragments.`);
    }
    const ids = [];
    const paths = new Set();
    for (const fragment of document.fragments) {
      if (!fragment || typeof fragment.id !== "string" || !fragment.id) {
        throw new Error(`README document ${document.id} contains an invalid fragment id.`);
      }
      if (ids.includes(fragment.id)) {
        throw new Error(`README document ${document.id} repeats fragment id ${fragment.id}.`);
      }
      ids.push(fragment.id);
      if (typeof fragment.path !== "string" || paths.has(fragment.path)) {
        throw new Error(`README document ${document.id} contains an invalid or duplicate fragment path.`);
      }
      paths.add(fragment.path);
      resolveWithin(path.join(repositoryRoot, README_DIRECTORY), fragment.path, `fragment ${fragment.id}`);
      if (!ALLOWED_MAINTAINERS.has(fragment.maintainer)) {
        throw new Error(`README fragment ${fragment.path} has unsupported maintainer ${fragment.maintainer}.`);
      }
    }
    if (!pairedFragmentIds) {
      pairedFragmentIds = ids;
    } else if (JSON.stringify(ids) !== JSON.stringify(pairedFragmentIds)) {
      throw new Error("English and Chinese README documents must use the same ordered fragment ids.");
    }
  }
}

export async function renderDocuments(repositoryRoot = DEFAULT_REPOSITORY_ROOT, overrides = new Map()) {
  const manifest = await loadManifest(repositoryRoot);
  const readmeRoot = path.join(repositoryRoot, README_DIRECTORY);
  const rendered = new Map();

  for (const document of manifest.documents) {
    const chunks = [normalizeFragment(manifest.generatedNotice)];
    for (const fragment of document.fragments) {
      const fragmentFile = resolveWithin(readmeRoot, fragment.path, `fragment ${fragment.id}`);
      const content = overrides.has(fragment.path)
        ? overrides.get(fragment.path)
        : await readFile(fragmentFile, "utf8");
      chunks.push(normalizeFragment(content));
    }
    rendered.set(document.output, `${chunks.join("\n\n")}\n`);
  }
  return rendered;
}

export async function writeRenderedDocuments({
  repositoryRoot = DEFAULT_REPOSITORY_ROOT,
  check = false,
  overrides = new Map(),
} = {}) {
  const rendered = await renderDocuments(repositoryRoot, overrides);
  const drifted = [];
  const changed = [];

  for (const [output, expected] of rendered) {
    const outputFile = resolveWithin(repositoryRoot, output, `README output ${output}`);
    let current = null;
    try {
      current = await readFile(outputFile, "utf8");
    } catch (error) {
      if (error.code !== "ENOENT") {
        throw error;
      }
    }
    if (current === expected) {
      continue;
    }
    if (check) {
      drifted.push(output);
    } else {
      await writeFile(outputFile, expected, "utf8");
      changed.push(output);
    }
  }
  return { drifted, changed };
}

export function fragmentsForMaintainer(manifest, maintainer, locale = null) {
  const fragments = [];
  for (const document of manifest.documents) {
    if (locale && document.locale !== locale) {
      continue;
    }
    for (const fragment of document.fragments) {
      if (fragment.maintainer === maintainer) {
        fragments.push({ ...fragment, documentId: document.id, locale: document.locale });
      }
    }
  }
  return fragments;
}

export function resolveFragmentPath(repositoryRoot, fragmentPath) {
  return resolveWithin(
    path.join(repositoryRoot, README_DIRECTORY),
    fragmentPath,
    `README fragment ${fragmentPath}`,
  );
}

export function normalizeFragment(value) {
  if (typeof value !== "string") {
    throw new Error("README fragment content must be a string.");
  }
  const normalized = value.replace(/\r\n?/g, "\n").trim();
  if (!normalized) {
    throw new Error("README fragments must not be empty.");
  }
  return normalized;
}

export function resolveWithin(base, relativePath, label) {
  if (typeof relativePath !== "string" || !relativePath || path.isAbsolute(relativePath)) {
    throw new Error(`${label} must be a non-empty relative path.`);
  }
  const normalizedBase = path.resolve(base);
  const resolved = path.resolve(normalizedBase, relativePath);
  if (resolved !== normalizedBase && !resolved.startsWith(`${normalizedBase}${path.sep}`)) {
    throw new Error(`${label} escapes its allowed directory.`);
  }
  return resolved;
}
