import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

const DOCS = [
  { path: "README.md", locale: "en" },
  { path: "README_CN.md", locale: "zh" },
];

const repository = resolveRepository();
const token = process.env.GITHUB_TOKEN || "";

const repoData = await fetchJson(`https://api.github.com/repos/${repository}`);
const contributors = await fetchContributors(repository);

for (const doc of DOCS) {
  const content = readFileSync(doc.path, "utf8");
  const updated = replaceSection(
    replaceSection(content, "COCO_STATS", renderStats(doc.locale, repository, repoData, contributors)),
    "COCO_CONTRIBUTORS",
    renderContributors(doc.locale, repository, contributors),
  );
  writeFileSync(doc.path, updated);
}

function resolveRepository() {
  if (process.env.GITHUB_REPOSITORY) {
    return process.env.GITHUB_REPOSITORY;
  }
  const remote = execFileSync("git", ["config", "--get", "remote.origin.url"], { encoding: "utf8" }).trim();
  const match = remote.match(/github\.com[:/](?<owner>[^/]+)\/(?<repo>[^/.]+)(?:\.git)?$/);
  if (!match?.groups) {
    return "patton174/coco-framework";
  }
  return `${match.groups.owner}/${match.groups.repo}`;
}

async function fetchContributors(repo) {
  const data = await fetchJson(`https://api.github.com/repos/${repo}/contributors?per_page=24&anon=false`);
  return data
    .filter((contributor) => contributor.type !== "Bot" && !contributor.login.endsWith("[bot]"))
    .slice(0, 12);
}

async function fetchJson(url) {
  const headers = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "coco-framework-readme-insights",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(url, { headers });
  if (!response.ok) {
    throw new Error(`GitHub API request failed: ${response.status} ${response.statusText} ${url}`);
  }
  return response.json();
}

function renderStats(locale, repo, data, contributorsList) {
  const labels = locale === "zh"
    ? { stars: "星标", forks: "派生", contributors: "贡献者", updated: "更新时间" }
    : { stars: "Stars", forks: "Forks", contributors: "Contributors", updated: "Updated" };
  const updatedAt = new Date().toISOString().slice(0, 10);
  const repoUrl = `https://github.com/${repo}`;

  return `<table>
  <tr>
    <td align="center"><strong>${formatNumber(data.stargazers_count)}</strong><br/>${labels.stars}</td>
    <td align="center"><strong>${formatNumber(data.forks_count)}</strong><br/>${labels.forks}</td>
    <td align="center"><strong>${formatNumber(contributorsList.length)}</strong><br/>${labels.contributors}</td>
    <td align="center"><a href="${repoUrl}">${labels.updated}: ${updatedAt}</a></td>
  </tr>
</table>`;
}

function renderContributors(locale, repo, contributorsList) {
  if (contributorsList.length === 0) {
    return locale === "zh"
      ? "<p>贡献者数据将在下一次 GitHub Actions 运行后更新。</p>"
      : "<p>Contributor data will be updated by the next GitHub Actions run.</p>";
  }
  const cells = contributorsList.map((contributor) => `    <td align="center">
      <a href="${contributor.html_url}">
        <img src="${contributor.avatar_url}&s=96" width="48" height="48" alt="${escapeHtml(contributor.login)}"/><br/>
        <sub>${escapeHtml(contributor.login)}</sub>
      </a>
    </td>`);
  return `<table>
  <tr>
${cells.join("\n")}
  </tr>
</table>
<p><a href="https://github.com/${repo}/graphs/contributors">${locale === "zh" ? "查看全部贡献者" : "View all contributors"}</a></p>`;
}

function replaceSection(content, name, replacement) {
  const start = `<!-- ${name}_START -->`;
  const end = `<!-- ${name}_END -->`;
  const startIndex = content.indexOf(start);
  const endIndex = content.indexOf(end);
  if (startIndex === -1 || endIndex === -1 || endIndex < startIndex) {
    throw new Error(`Missing ${name} markers.`);
  }
  return `${content.slice(0, startIndex + start.length)}\n${replacement}\n${content.slice(endIndex)}`;
}

function formatNumber(value) {
  return new Intl.NumberFormat("en-US").format(value || 0);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
