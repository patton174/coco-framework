import { readFile, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

import {
  DEFAULT_REPOSITORY_ROOT,
  fragmentsForMaintainer,
  loadManifest,
  resolveFragmentPath,
} from "./readme-lib.mjs";

const MAX_CONTRIBUTOR_PAGES = 10;
const CONTRIBUTORS_PER_PAGE = 100;
const DISPLAYED_CONTRIBUTORS = 12;

export async function updateInsights({
  repositoryRoot = DEFAULT_REPOSITORY_ROOT,
  repository,
  date = new Date().toISOString().slice(0, 10),
  fixturePath = null,
  token = process.env.GH_TOKEN || process.env.GITHUB_TOKEN || "",
  fetchImpl = globalThis.fetch,
} = {}) {
  const normalizedRepository = validateRepository(repository || process.env.GITHUB_REPOSITORY || "patton174/coco-framework");
  const normalizedDate = validateDate(date);
  const rawInsights = fixturePath
    ? JSON.parse(await readFile(fixturePath, "utf8"))
    : await fetchGitHubInsights(normalizedRepository, token, fetchImpl);
  const insights = normalizeInsights(rawInsights);
  const manifest = await loadManifest(repositoryRoot);
  const scriptFragments = fragmentsForMaintainer(manifest, "script");

  if (scriptFragments.length !== manifest.documents.length) {
    throw new Error("Each README document must define exactly one script-maintained fragment.");
  }

  const changed = [];
  for (const fragment of scriptFragments) {
    const fragmentFile = resolveFragmentPath(repositoryRoot, fragment.path);
    const current = await readFile(fragmentFile, "utf8");
    const updated = replaceMarkedSection(
      replaceMarkedSection(
        current,
        "COCO_STATS",
        renderStats(fragment.locale, normalizedRepository, normalizedDate, insights),
      ),
      "COCO_CONTRIBUTORS",
      renderContributors(fragment.locale, normalizedRepository, insights.contributors),
    );
    if (updated !== current) {
      await writeFile(fragmentFile, updated, "utf8");
      changed.push(fragment.path);
    }
  }
  return { changed, insights };
}

export async function fetchGitHubInsights(repository, token, fetchImpl = globalThis.fetch) {
  if (typeof fetchImpl !== "function") {
    throw new Error("A Fetch API implementation is required.");
  }
  const headers = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "coco-framework-readme-insights",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const repositoryData = await fetchJson(
    `https://api.github.com/repos/${repository}`,
    headers,
    fetchImpl,
  );
  const contributors = [];
  for (let page = 1; page <= MAX_CONTRIBUTOR_PAGES; page += 1) {
    const batch = await fetchJson(
      `https://api.github.com/repos/${repository}/contributors?per_page=${CONTRIBUTORS_PER_PAGE}&anon=false&page=${page}`,
      headers,
      fetchImpl,
    );
    if (!Array.isArray(batch)) {
      throw new Error("GitHub contributors response must be an array.");
    }
    contributors.push(...batch);
    if (batch.length < CONTRIBUTORS_PER_PAGE) {
      break;
    }
    if (page === MAX_CONTRIBUTOR_PAGES) {
      throw new Error("GitHub contributors response exceeded the configured page limit.");
    }
  }
  return { repository: repositoryData, contributors };
}

async function fetchJson(url, headers, fetchImpl) {
  const response = await fetchImpl(url, { headers });
  if (!response.ok) {
    throw new Error(`GitHub API request failed: ${response.status} ${response.statusText} ${url}`);
  }
  return response.json();
}

export function normalizeInsights(rawInsights) {
  if (!rawInsights || typeof rawInsights !== "object") {
    throw new Error("Insights payload must be an object.");
  }
  const repository = rawInsights.repository;
  if (!repository || typeof repository !== "object") {
    throw new Error("Insights payload must include repository data.");
  }
  const contributors = Array.isArray(rawInsights.contributors) ? rawInsights.contributors : [];
  const normalizedContributors = contributors
    .filter((contributor) => contributor && typeof contributor.login === "string")
    .filter((contributor) => contributor.type !== "Bot" && !contributor.login.endsWith("[bot]"))
    .filter((contributor) => /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/.test(contributor.login))
    .filter((contributor, index, list) => list.findIndex((candidate) => candidate.login === contributor.login) === index)
    .map((contributor) => ({ login: contributor.login }));

  return {
    stars: nonNegativeInteger(repository.stargazers_count, "stargazers_count"),
    forks: nonNegativeInteger(repository.forks_count, "forks_count"),
    contributors: normalizedContributors,
  };
}

export function renderStats(locale, repository, date, insights) {
  const labels = locale === "zh-CN"
    ? { stars: "星标", forks: "派生", contributors: "贡献者", updated: "更新时间" }
    : { stars: "Stars", forks: "Forks", contributors: "Contributors", updated: "Updated" };
  const repositoryUrl = `https://github.com/${repository}`;
  return `<table>
  <tr>
    <td align="center"><strong>${formatNumber(insights.stars)}</strong><br/>${labels.stars}</td>
    <td align="center"><strong>${formatNumber(insights.forks)}</strong><br/>${labels.forks}</td>
    <td align="center"><strong>${formatNumber(insights.contributors.length)}</strong><br/>${labels.contributors}</td>
    <td align="center"><a href="${repositoryUrl}">${labels.updated}: ${date}</a></td>
  </tr>
</table>`;
}

export function renderContributors(locale, repository, contributors) {
  if (contributors.length === 0) {
    return locale === "zh-CN"
      ? "<p>贡献者数据将在下一次维护运行后更新。</p>"
      : "<p>Contributor data will be updated by the next maintenance run.</p>";
  }

  const rows = [];
  const displayed = contributors.slice(0, DISPLAYED_CONTRIBUTORS);
  for (let index = 0; index < displayed.length; index += 6) {
    const cells = displayed.slice(index, index + 6).map(({ login }) => {
      const encodedLogin = encodeURIComponent(login);
      const escapedLogin = escapeHtml(login);
      return `    <td align="center">
      <a href="https://github.com/${encodedLogin}">
        <img src="https://avatars.githubusercontent.com/${encodedLogin}?s=96" width="48" height="48" alt="${escapedLogin}"/><br/>
        <sub>${escapedLogin}</sub>
      </a>
    </td>`;
    });
    rows.push(`  <tr>\n${cells.join("\n")}\n  </tr>`);
  }

  const linkLabel = locale === "zh-CN" ? "查看全部贡献者" : "View all contributors";
  return `<table>
${rows.join("\n")}
</table>
<p><a href="https://github.com/${repository}/graphs/contributors">${linkLabel}</a></p>`;
}

export function replaceMarkedSection(content, name, replacement) {
  const start = `<!-- ${name}_START -->`;
  const end = `<!-- ${name}_END -->`;
  const startIndex = content.indexOf(start);
  const endIndex = content.indexOf(end);
  if (startIndex === -1 || endIndex === -1 || endIndex < startIndex) {
    throw new Error(`Missing or misordered ${name} markers.`);
  }
  if (content.indexOf(start, startIndex + start.length) !== -1 || content.indexOf(end, endIndex + end.length) !== -1) {
    throw new Error(`Duplicate ${name} markers.`);
  }
  return `${content.slice(0, startIndex + start.length)}\n${replacement}\n${content.slice(endIndex)}`;
}

export function validateRepository(repository) {
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository)) {
    throw new Error(`Invalid GitHub repository: ${repository}`);
  }
  return repository;
}

export function validateDate(date) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || new Date(`${date}T00:00:00Z`).toISOString().slice(0, 10) !== date) {
    throw new Error(`Invalid UTC date: ${date}`);
  }
  return date;
}

function nonNegativeInteger(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${label} must be a non-negative integer.`);
  }
  return value;
}

function formatNumber(value) {
  return new Intl.NumberFormat("en-US").format(value);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function parseArguments(args) {
  const options = {};
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (!["--repository", "--date", "--fixture"].includes(argument) || !args[index + 1]) {
      throw new Error(
        "Usage: node .github/readme/scripts/update-insights.mjs [--repository owner/repo] [--date YYYY-MM-DD] [--fixture path]",
      );
    }
    options[argument.slice(2)] = args[index + 1];
    index += 1;
  }
  return options;
}

export async function main(args = process.argv.slice(2)) {
  const options = parseArguments(args);
  const result = await updateInsights({
    repository: options.repository,
    date: options.date,
    fixturePath: options.fixture,
  });
  process.stdout.write(
    result.changed.length > 0
      ? `Updated ${result.changed.join(", ")}.\n`
      : "README insights are already up to date.\n",
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
