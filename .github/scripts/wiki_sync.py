"""Validate ``docs/wiki`` and mirror it into a local GitHub Wiki checkout.

``docs/wiki`` is the single source of truth for the public GitHub Wiki. This
tool only touches the local file system and the local wiki checkout. It never
contacts a remote and never runs ``git push``; pushing stays a manual step.

Subcommands
-----------
``check`` (alias ``validate``)
    Validate the page set. Problems are printed to stderr as
    ``<path>:<line>: <rule>: <message>``, sorted and without timestamps.
``sync --target <dir>``
    Mirror the page set into ``<dir>`` without calling git.
``publish <wiki-checkout>``
    Run ``check``, mirror the page set into a git checkout of the wiki, and
    create one local commit with a deterministic message. Publishing the same
    content twice creates no second commit.

Exit codes: ``0`` success, ``1`` validation failure or (``sync --check``) drift,
``2`` usage, I/O, or unsafe-target errors.

Wiki links are written ``[text](Page-Name)`` or ``[[Text|Page-Name]]`` (the
target is the part after the pipe). Repository files are linked with absolute
``https://github.com/patton174/coco-framework/blob/main/<path>`` URLs
(``tree/main`` for directories).

Rules
-----
W001 flat layout, only ``.md`` files, no hidden files
W002 ASCII ``Title-Case-With-Hyphens.md`` page names, no case-only duplicates
W003 ``Home.md``, ``_Sidebar.md``, ``_Footer.md`` exist
W005 UTF-8, no BOM, LF only, exactly one trailing newline
W006 no YAML front matter
W010 link shape: no ``.md``, ``/``, ``\\``, ``./``, ``../`` or drive-letter links
W011 wiki links resolve to an existing page (exact case)
W012 ``#anchor`` matches a heading slug in the target page
W013 repository URLs resolve in the working tree (exact case, correct kind)
W020 code fences are balanced
W021 mermaid fences are non-empty and start with a known diagram type
W030 hygiene: unresolved markers, local paths, machine wording, e-mail addresses
W031 secret-looking strings
W040 ``_Sidebar.md`` links every content page
W041 ``_Footer.md`` links the repository, CHANGELOG, CONTRIBUTING and LICENSE
W042 every content page is reachable from ``Home.md``
W050 unsafe mirror target
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys
from collections import deque
from collections.abc import Iterator, Sequence
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import unquote

REPOSITORY_URL = "https://github.com/patton174/coco-framework"
DEFAULT_WIKI_DIR = "docs/wiki"
EXCLUDED_FILES = frozenset({"README.md"})
HOME_PAGE = "Home.md"
SIDEBAR_PAGE = "_Sidebar.md"
FOOTER_PAGE = "_Footer.md"
SPECIAL_PAGES = (HOME_PAGE, SIDEBAR_PAGE, FOOTER_PAGE)
NAVIGATION_PAGES = frozenset({SIDEBAR_PAGE, FOOTER_PAGE})
PAGE_NAME_PATTERN = re.compile(r"[A-Z][A-Za-z0-9]*(?:-[A-Z0-9][A-Za-z0-9]*)*\.md")

# Repository paths that wiki pages may link to although they are absent from the
# working tree, for example a file that only exists after a pull request merges.
# Entries are exact repository-relative paths, or a prefix ending in "/". Keep
# this empty unless a link must be published before its target exists, and name
# the reason in a comment next to every entry. ``--allow-path`` adds entries for
# a single run.
REPO_PATH_ALLOWLIST: tuple[str, ...] = ()
FORBIDDEN_REPO_ROOTS = frozenset(
    {".agents", ".claude", ".superpowers", ".worktrees", ".git"}
)
FOOTER_REQUIRED_URLS = (
    f"{REPOSITORY_URL}/blob/main/CHANGELOG.md",
    f"{REPOSITORY_URL}/blob/main/CONTRIBUTING.md",
    f"{REPOSITORY_URL}/blob/main/LICENSE",
)
MERMAID_DIAGRAM_TYPES = frozenset(
    {
        "architecture-beta",
        "block-beta",
        "C4Component",
        "C4Container",
        "C4Context",
        "C4Deployment",
        "C4Dynamic",
        "classDiagram",
        "classDiagram-v2",
        "erDiagram",
        "flowchart",
        "gantt",
        "gitGraph",
        "graph",
        "journey",
        "kanban",
        "mindmap",
        "packet-beta",
        "pie",
        "quadrantChart",
        "requirementDiagram",
        "sankey-beta",
        "sequenceDiagram",
        "stateDiagram",
        "stateDiagram-v2",
        "timeline",
        "xychart-beta",
    }
)
# `publish` may only run these local git subcommands; there is deliberately no
# network subcommand (push, fetch, pull, clone, remote) in this list.
ALLOWED_GIT_COMMANDS = frozenset(
    {"add", "commit", "diff", "ls-files", "rev-parse", "status", "var"}
)

HYGIENE_RULES: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("unresolved-marker", re.compile("待确认")),
    ("absolute-local-path", re.compile(r"(?<![A-Za-z0-9])[A-Za-z]:\\")),
    ("unix-home-path", re.compile(r"(?<![\w.-])/(?:Users|home)/[A-Za-z0-9._-]+/")),
    ("local-file-url", re.compile(r"file:///")),
    ("local-jdk-path", re.compile(r"jdk_1\.8")),
    ("machine-specific-wording", re.compile("本机|维护者的机器|整理本 Wiki")),
    ("internal-commit-reference", re.compile(r"\b12515b4\b")),
    ("jvm-crash-log-name", re.compile(r"\b(?:hs_err_pid|replay_pid)\d+")),
    ("draft-area-path", re.compile(r"(?:\.agents|\.claude|\.superpowers)[/\\]")),
    ("plan-directory", re.compile(r"(?<![A-Za-z0-9])_plan(?![A-Za-z0-9_])")),
    ("uncommitted-changes", re.compile("未提交的(?:改动|修改)")),
    (
        "email-address",
        re.compile(
            r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}"
        ),
    ),
    ("legacy-wiki-path", re.compile(r"\b(?:modules|guides)/[A-Za-z0-9-]+\.md")),
)
SECRET_RULES: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("github-token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}")),
    ("github-fine-grained-token", re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}")),
    ("aws-access-key-id", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("private-key-block", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("slack-token", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}")),
    (
        "json-web-token",
        re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"),
    ),
)

FENCE_OPEN = re.compile(r"^\s*(?P<fence>`{3,}|~{3,})(?P<info>.*)$")
FENCE_CLOSE = re.compile(r"^\s*(?P<fence>`{3,}|~{3,})\s*$")
INLINE_CODE = re.compile(r"(?<!`)(`+)(?!`)(.+?)(?<!`)\1(?!`)")
HEADING = re.compile(r"^ {0,3}#{1,6}[ \t]+(?P<text>.*?)(?:[ \t]+#+)?[ \t]*$")
HTML_ANCHOR = re.compile(r"""<a\s[^>]*?\b(?:name|id)\s*=\s*["']([^"']+)["']""", re.I)
HTML_ATTRIBUTE = re.compile(r"""\b(?:href|src)\s*=\s*(?:"([^"]*)"|'([^']*)')""", re.I)
INLINE_LINK = re.compile(
    r"\[(?P<text>(?:[^\[\]\\]|\\.|\[[^\[\]]*\])*)\]"
    r"\(\s*(?P<target><[^<>]*>|(?:[^\s()]|\([^\s()]*\))*)"
    r"(?:\s+(?:\"[^\"]*\"|'[^']*'))?\s*\)"
)
REFERENCE_DEFINITION = re.compile(r"^ {0,3}\[[^\]\n]+\]:\s*(?P<target><[^>]*>|\S+)")
WIKI_LINK = re.compile(r"\[\[(?P<body>[^\[\]\n|]+(?:\|[^\[\]\n|]+)?)\]\]")
REPOSITORY_FILE_URL = re.compile(
    r"https?://github\.com/patton174/coco-framework/(?P<kind>blob|tree)/main"
    r"(?![A-Za-z0-9_.-])(?P<rest>/[^\s)>\]\"'`<]*)?",
    re.I,
)
REPOSITORY_ROOT_URL = re.compile(
    re.escape(REPOSITORY_URL) + r"(?![A-Za-z0-9_-])(?!\.[A-Za-z0-9])(?!/[A-Za-z0-9])"
)
DRIVE_PATH = re.compile(r"^[A-Za-z]:[\\/]")
URL_SCHEME = re.compile(r"^(?P<scheme>[A-Za-z][A-Za-z0-9+.-]*):")
EXTERNAL_SCHEMES = frozenset({"http", "https", "mailto"})


class WikiSyncError(RuntimeError):
    """Usage or I/O failure; reported on stderr with exit code 2."""


class TargetError(WikiSyncError):
    """The mirror target failed the W050 safety checks."""

    def __init__(self, target: str, message: str) -> None:
        super().__init__(message)
        self.target = target
        self.message = message

    def render(self) -> str:
        return f"{self.target}:1: W050: {self.message}"


@dataclass(frozen=True, order=True)
class Problem:
    path: str
    line: int
    rule: str
    message: str

    def render(self) -> str:
        return f"{self.path}:{self.line}: {self.rule}: {self.message}"


@dataclass
class Fence:
    start: int
    char: str
    length: int
    info: str
    body: list[tuple[int, str]] = field(default_factory=list)
    closed: bool = False


@dataclass
class Page:
    name: str
    lines: list[str]
    visible: list[str]
    prose: list[str]
    fences: list[Fence]
    headings: list[tuple[int, str]]
    slugs: set[str]
    anchors: set[str]


@dataclass
class PageStats:
    internal_links: int = 0
    repository_links: int = 0
    external_links: int = 0
    mermaid_fences: int = 0
    headings: int = 0


@dataclass(frozen=True)
class CheckResult:
    problems: tuple[Problem, ...]
    page_stats: dict[str, PageStats]

    @property
    def page_count(self) -> int:
        return len(self.page_stats)


@dataclass(frozen=True)
class MirrorAction:
    action: str
    name: str


def display_path(path: Path, repo_root: Path) -> str:
    try:
        return path.resolve().relative_to(repo_root.resolve()).as_posix()
    except ValueError:
        return path.as_posix()


def heading_slug(text: str) -> str:
    """Return the GitHub anchor slug for a heading's text."""
    text = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = text.replace("`", "").replace("*", "").strip().lower()
    return re.sub(r"[^\w\- ]", "", text).replace(" ", "-")


def _strip_comments(text: str, in_comment: bool) -> tuple[str, bool]:
    pieces: list[str] = []
    position = 0
    while position < len(text):
        if in_comment:
            end = text.find("-->", position)
            if end == -1:
                return "".join(pieces), True
            position = end + 3
            in_comment = False
        else:
            start = text.find("<!--", position)
            if start == -1:
                pieces.append(text[position:])
                break
            pieces.append(text[position:start])
            position = start + 4
            in_comment = True
    return "".join(pieces), in_comment


def parse_page(name: str, lines: list[str]) -> Page:
    """Split a page into code fences, visible text, prose, and headings."""
    visible: list[str] = []
    prose: list[str] = []
    fences: list[Fence] = []
    headings: list[tuple[int, str]] = []
    anchors: set[str] = set()
    open_fence: Fence | None = None
    in_comment = False

    for line_number, raw in enumerate(lines, start=1):
        if open_fence is not None:
            closing = FENCE_CLOSE.match(raw)
            if (
                closing
                and closing.group("fence")[0] == open_fence.char
                and len(closing.group("fence")) >= open_fence.length
            ):
                open_fence.closed = True
                open_fence = None
            else:
                open_fence.body.append((line_number, raw))
            visible.append("")
            prose.append("")
            continue

        opening = FENCE_OPEN.match(raw)
        if opening:
            fence_text = opening.group("fence")
            info = opening.group("info").strip()
            if not (fence_text[0] == "`" and "`" in info):
                open_fence = Fence(line_number, fence_text[0], len(fence_text), info)
                fences.append(open_fence)
                visible.append("")
                prose.append("")
                continue

        text, in_comment = _strip_comments(raw, in_comment)
        visible.append(text)
        prose.append(INLINE_CODE.sub(lambda match: " " * len(match.group(0)), text))
        heading = HEADING.match(text)
        if heading and heading.group("text"):
            headings.append((line_number, heading.group("text")))
        anchors.update(match.lower() for match in HTML_ANCHOR.findall(text))

    slugs: set[str] = set()
    seen: dict[str, int] = {}
    for _, heading_text in headings:
        slug = heading_slug(heading_text)
        count = seen.get(slug, 0)
        seen[slug] = count + 1
        slugs.add(slug if count == 0 else f"{slug}-{count}")
    return Page(name, lines, visible, prose, fences, headings, slugs, anchors)


def iter_link_targets(text: str) -> Iterator[str]:
    for match in INLINE_LINK.finditer(text):
        yield match.group("target")
        yield from iter_link_targets(match.group("text"))
    definition = REFERENCE_DEFINITION.match(text)
    if definition:
        yield definition.group("target")


class WikiChecker:
    """Apply the wiki validation rules to one ``docs/wiki`` directory."""

    def __init__(
        self, wiki_dir: Path, repo_root: Path, allow_paths: Sequence[str] = ()
    ) -> None:
        self.wiki_dir = wiki_dir
        self.repo_root = repo_root
        self.allow_paths = tuple(
            entry.strip().removeprefix("./").lstrip("/")
            for entry in (*REPO_PATH_ALLOWLIST, *allow_paths)
            if entry.strip()
        )
        wiki_display = display_path(wiki_dir, repo_root)
        self.display_prefix = "" if wiki_display == "." else f"{wiki_display}/"
        self.problems: list[Problem] = []
        self.pages: dict[str, Page] = {}
        self.stats: dict[str, PageStats] = {}
        self.content_stems: set[str] = set()
        self.graph: dict[str, set[str]] = {}
        self._listings: dict[Path, list[str] | None] = {}

    def run(self) -> CheckResult:
        candidates = self._check_layout(sorted(os.listdir(self.wiki_dir)))
        self.content_stems = {
            name.removesuffix(".md")
            for name in candidates
            if name not in NAVIGATION_PAGES
        }
        for name in candidates:
            page = self._load_page(name)
            if page is not None:
                self.pages[name] = page
        for name in sorted(self.pages):
            self._check_page(self.pages[name])
        self._check_navigation()
        for name in candidates:
            self.stats.setdefault(name, PageStats())
        return CheckResult(
            tuple(sorted(set(self.problems))), dict(sorted(self.stats.items()))
        )

    def _add(self, name: str, line: int, rule: str, message: str) -> None:
        self.problems.append(
            Problem(f"{self.display_prefix}{name}", line, rule, message)
        )

    def _check_layout(self, entries: list[str]) -> list[str]:
        candidates: list[str] = []
        for entry in entries:
            if (self.wiki_dir / entry).is_dir():
                self._add(entry, 1, "W001", "subdirectories are not allowed")
            elif entry.startswith("."):
                self._add(entry, 1, "W001", "hidden files are not allowed")
            elif not entry.endswith(".md"):
                self._add(entry, 1, "W001", "only .md files are allowed")
            elif entry not in EXCLUDED_FILES:
                candidates.append(entry)

        for entry in candidates:
            if entry not in SPECIAL_PAGES and not PAGE_NAME_PATTERN.fullmatch(entry):
                reason = "non-ASCII characters" if not entry.isascii() else "bad shape"
                self._add(
                    entry,
                    1,
                    "W002",
                    f"invalid wiki page name ({reason}); "
                    "use ASCII Title-Case-With-Hyphens.md",
                )
        by_folded: dict[str, list[str]] = {}
        for entry in entries:
            if entry.endswith(".md"):
                by_folded.setdefault(entry.casefold(), []).append(entry)
        for group in by_folded.values():
            for entry in sorted(group)[1:]:
                self._add(
                    entry, 1, "W002", f"page name differs only by case from {group[0]}"
                )
        for special in SPECIAL_PAGES:
            if special not in entries:
                self._add(special, 1, "W003", f"required page {special} is missing")
        return candidates

    def _load_page(self, name: str) -> Page | None:
        path = self.wiki_dir / name
        try:
            data = path.read_bytes()
        except OSError as error:
            raise WikiSyncError(f"cannot read {path.as_posix()}: {error}") from error
        if data.startswith(b"\xef\xbb\xbf"):
            self._add(name, 1, "W005", "file starts with a UTF-8 byte order mark")
            data = data[3:]
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError as error:
            line = data.count(b"\n", 0, error.start) + 1
            self._add(name, line, "W005", f"file is not valid UTF-8 ({error.reason})")
            return None
        if "\r" in text:
            line = text.count("\n", 0, text.index("\r")) + 1
            self._add(name, line, "W005", "carriage return found; use LF line endings")
            text = text.replace("\r\n", "\n").replace("\r", "\n")
        lines = text.split("\n")
        if lines and lines[-1] == "":
            lines.pop()
        if not text:
            self._add(name, 1, "W005", "file is empty")
        elif not text.endswith("\n"):
            self._add(name, len(lines), "W005", "file must end with a newline")
        elif text.endswith("\n\n"):
            self._add(name, len(lines), "W005", "file must end with one newline only")
        return parse_page(name, lines)

    def _check_page(self, page: Page) -> None:
        stats = self.stats.setdefault(page.name, PageStats())
        stats.headings = len(page.headings)
        self.graph.setdefault(page.name, set())
        if page.lines and page.lines[0].startswith("---"):
            self._add(page.name, 1, "W006", "YAML front matter is not allowed")
        self._check_fences(page, stats)
        self._check_links(page, stats)
        self._check_repository_urls(page)
        self._check_hygiene(page)

    def _check_fences(self, page: Page, stats: PageStats) -> None:
        for fence in page.fences:
            if not fence.closed:
                self._add(
                    page.name,
                    fence.start,
                    "W020",
                    f"code fence opened with {fence.char * fence.length} "
                    "is never closed",
                )
            if fence.info.split(maxsplit=1)[:1] == ["mermaid"]:
                stats.mermaid_fences += 1
                self._check_mermaid(page, fence)

    def _check_mermaid(self, page: Page, fence: Fence) -> None:
        content = [(n, text.strip()) for n, text in fence.body if text.strip()]
        index = 0
        if content and content[0][1] == "---":
            index = next(
                (i + 1 for i in range(1, len(content)) if content[i][1] == "---"),
                len(content),
            )
        while index < len(content) and content[index][1].startswith("%%"):
            index += 1
        if index >= len(content):
            self._add(page.name, fence.start, "W021", "mermaid block is empty")
            return
        number, first_line = content[index]
        diagram = first_line.split()[0].rstrip(";")
        if diagram not in MERMAID_DIAGRAM_TYPES and not diagram.startswith("flowchart"):
            self._add(
                page.name, number, "W021", f"unknown mermaid diagram type {diagram!r}"
            )

    def _check_links(self, page: Page, stats: PageStats) -> None:
        for line_number, text in enumerate(page.prose, start=1):
            if not text.strip():
                continue
            for target in iter_link_targets(text):
                self._check_target(page, line_number, target, stats)
            for match in WIKI_LINK.finditer(text):
                stats.internal_links += 1
                target = match.group("body").split("|")[-1].strip().replace(" ", "-")
                self._check_internal(page, line_number, target)
            for match in HTML_ATTRIBUTE.finditer(text):
                value = (match.group(1) or match.group(2) or "").strip()
                self._check_html_target(page, line_number, value, stats)

    def _check_target(
        self, page: Page, line: int, target: str, stats: PageStats
    ) -> None:
        raw = target.strip()
        if raw.startswith("<") and raw.endswith(">"):
            raw = raw[1:-1].strip()
        scheme = URL_SCHEME.match(raw)
        if not raw:
            self._add(page.name, line, "W010", "link has an empty target")
        elif REPOSITORY_FILE_URL.match(raw):
            stats.repository_links += 1
        elif raw.startswith("#"):
            stats.internal_links += 1
            self._check_anchor(page, line, page, unquote(raw[1:]))
        elif DRIVE_PATH.match(raw):
            self._add(page.name, line, "W010", f"absolute local path link {raw!r}")
        elif scheme and scheme.group("scheme").lower() in EXTERNAL_SCHEMES:
            stats.external_links += 1
        elif scheme:
            self._add(
                page.name,
                line,
                "W010",
                f"unsupported link scheme {scheme.group('scheme')!r}",
            )
        else:
            stats.internal_links += 1
            self._check_internal(page, line, raw)

    def _check_internal(self, page: Page, line: int, raw: str) -> None:
        page_part, _, fragment = raw.partition("#")
        page_part = unquote(page_part)
        if "/" in page_part or "\\" in page_part:
            self._add(
                page.name,
                line,
                "W010",
                f"path-style link {raw!r}; use [text](Page-Name) for wiki pages "
                "or an absolute https://github.com/ URL for repository files",
            )
            return
        if page_part.lower().endswith(".md"):
            self._add(
                page.name,
                line,
                "W010",
                f"link {raw!r} ends with .md; drop the extension",
            )
            return
        if page_part not in self.content_stems:
            folded = sorted(
                stem
                for stem in self.content_stems
                if stem.casefold() == page_part.casefold()
            )
            hint = f"; did you mean {folded[0]!r}" if folded else ""
            self._add(
                page.name, line, "W011", f"no wiki page named {page_part!r}{hint}"
            )
            return
        self.graph.setdefault(page.name, set()).add(page_part)
        if fragment:
            self._check_anchor(
                page, line, self.pages.get(f"{page_part}.md"), unquote(fragment)
            )

    def _check_html_target(
        self, page: Page, line: int, value: str, stats: PageStats
    ) -> None:
        if REPOSITORY_FILE_URL.match(value):
            stats.repository_links += 1
        elif value.startswith(("https://", "http://", "mailto:")):
            stats.external_links += 1
        elif value.startswith("#"):
            stats.internal_links += 1
            self._check_anchor(page, line, page, unquote(value[1:]))
        else:
            self._add(
                page.name,
                line,
                "W010",
                f"HTML href/src {value!r} must be an absolute https:// URL",
            )

    def _check_anchor(
        self, page: Page, line: int, target: Page | None, fragment: str
    ) -> None:
        if target is None or not fragment:
            return
        if fragment.lower() not in target.slugs | target.anchors:
            self._add(
                page.name,
                line,
                "W012",
                f"anchor #{fragment} does not match a heading in {target.name}",
            )

    def _check_repository_urls(self, page: Page) -> None:
        for line_number, text in enumerate(page.prose, start=1):
            for match in REPOSITORY_FILE_URL.finditer(text):
                problem = self._repository_problem(match)
                if problem:
                    self._add(page.name, line_number, "W013", problem)

    def _repository_problem(self, match: re.Match[str]) -> str | None:
        kind = match.group("kind").lower()
        rest = (match.group("rest") or "").rstrip(".,;:!?")
        rest = re.split(r"[#?]", rest, maxsplit=1)[0]
        url = f"{REPOSITORY_URL}/{kind}/main{rest}"
        path = unquote(rest).strip("/")
        parts = [part for part in path.split("/") if part]
        if any(part in {".", ".."} for part in parts):
            return f"{url}: path contains '.' or '..' segments"
        if parts and parts[0] in FORBIDDEN_REPO_ROOTS:
            return f"{url}: links into {parts[0]}/ are not allowed"
        if self._is_allowed(path):
            return None
        if kind == "blob" and not parts:
            return f"{url}: blob URL has no file path"
        if kind == "blob" and rest.endswith("/"):
            return f"{url}: blob URL must not end with '/'; use tree/main"
        current = self.repo_root
        for index, part in enumerate(parts):
            names = self._listing(current)
            where = "/".join(parts[:index]) or "."
            if names is None:
                return f"{url}: '{where}' is not a directory"
            if part not in names:
                folded = sorted(n for n in names if n.casefold() == part.casefold())
                hint = f"; did you mean '{folded[0]}'" if folded else ""
                return f"{url}: '{part}' not found under '{where}'{hint}"
            current = current / part
        if kind == "blob" and not current.is_file():
            return f"{url}: blob URL points at a directory; use tree/main"
        if kind == "tree" and not current.is_dir():
            return f"{url}: tree URL points at a file; use blob/main"
        return None

    def _is_allowed(self, path: str) -> bool:
        return any(
            path == entry.rstrip("/")
            or (entry.endswith("/") and path.startswith(entry))
            for entry in self.allow_paths
        )

    def _listing(self, directory: Path) -> list[str] | None:
        if directory not in self._listings:
            try:
                self._listings[directory] = os.listdir(directory)
            except OSError:
                self._listings[directory] = None
        return self._listings[directory]

    def _check_hygiene(self, page: Page) -> None:
        for line_number, text in enumerate(page.lines, start=1):
            for label, pattern in HYGIENE_RULES:
                match = pattern.search(text)
                if match:
                    self._add(
                        page.name,
                        line_number,
                        "W030",
                        f"hygiene rule {label!r} matched {match.group(0)!r}",
                    )
            for label, pattern in SECRET_RULES:
                if pattern.search(text):
                    self._add(
                        page.name,
                        line_number,
                        "W031",
                        f"secret-looking string ({label}); reference only the key name",
                    )

    def _check_navigation(self) -> None:
        if SIDEBAR_PAGE in self.pages:
            linked = self.graph.get(SIDEBAR_PAGE, set())
            for stem in sorted(self.content_stems - linked):
                self._add(
                    SIDEBAR_PAGE, 1, "W040", f"page {stem} is not linked from sidebar"
                )
        footer = self.pages.get(FOOTER_PAGE)
        if footer is not None:
            text = "\n".join(footer.prose)
            if not REPOSITORY_ROOT_URL.search(text):
                self._add(
                    FOOTER_PAGE, 1, "W041", f"footer must link to {REPOSITORY_URL}"
                )
            for url in FOOTER_REQUIRED_URLS:
                if url not in text:
                    self._add(FOOTER_PAGE, 1, "W041", f"footer must link to {url}")
        if HOME_PAGE in self.pages:
            reached = {"Home"}
            queue = deque(["Home"])
            while queue:
                for stem in sorted(self.graph.get(f"{queue.popleft()}.md", set())):
                    if stem not in reached and stem in self.content_stems:
                        reached.add(stem)
                        queue.append(stem)
            for stem in sorted(self.content_stems - reached):
                self._add(
                    HOME_PAGE, 1, "W042", f"page {stem} is not reachable from Home"
                )


def check_wiki(
    wiki_dir: Path, repo_root: Path, allow_paths: Sequence[str] = ()
) -> CheckResult:
    if not wiki_dir.is_dir():
        raise WikiSyncError(f"wiki directory not found: {wiki_dir.as_posix()}")
    return WikiChecker(wiki_dir, repo_root, allow_paths).run()


def format_stats(result: CheckResult) -> list[str]:
    totals = PageStats()
    for stats in result.page_stats.values():
        totals.internal_links += stats.internal_links
        totals.repository_links += stats.repository_links
        totals.external_links += stats.external_links
        totals.mermaid_fences += stats.mermaid_fences
        totals.headings += stats.headings

    def render(label: str, stats: PageStats) -> str:
        return (
            f"stats: {label} internal_links={stats.internal_links} "
            f"repository_links={stats.repository_links} "
            f"external_links={stats.external_links} "
            f"mermaid_fences={stats.mermaid_fences} headings={stats.headings}"
        )

    return [
        f"stats: pages={result.page_count}",
        render("total", totals),
        *(render(name, stats) for name, stats in result.page_stats.items()),
    ]


def collect_source_pages(wiki_dir: Path) -> dict[str, bytes]:
    pages: dict[str, bytes] = {}
    for entry in sorted(os.listdir(wiki_dir)):
        path = wiki_dir / entry
        if entry.endswith(".md") and entry not in EXCLUDED_FILES and path.is_file():
            pages[entry] = path.read_bytes()
    return pages


def ensure_safe_target(target: Path, wiki_dir: Path, repo_root: Path) -> None:
    shown = target.as_posix()
    if not target.is_dir():
        raise TargetError(shown, "target must be an existing directory")
    resolved, source, root = target.resolve(), wiki_dir.resolve(), repo_root.resolve()
    if (
        resolved == source
        or resolved.is_relative_to(source)
        or source.is_relative_to(resolved)
    ):
        raise TargetError(shown, "target overlaps the source wiki directory")
    if resolved == root or root.is_relative_to(resolved):
        raise TargetError(shown, "target is, or contains, the repository root")
    for marker in ("pom.xml", "AGENTS.md"):
        if (target / marker).exists():
            raise TargetError(
                shown, f"target contains {marker}; it looks like the main repository"
            )


def plan_mirror(
    source: dict[str, bytes], target: Path, prune: bool
) -> list[MirrorAction]:
    existing = {
        entry: target / entry
        for entry in os.listdir(target)
        if entry.endswith(".md") and (target / entry).is_file()
    }
    folded_source = {name.casefold() for name in source}
    actions: list[MirrorAction] = []
    for name in sorted(source):
        current = existing.get(name)
        if current is None:
            actions.append(MirrorAction("ADD", name))
        elif current.read_bytes().replace(b"\r\n", b"\n") == source[name]:
            # A Windows clone with core.autocrlf=true checks pages out with CRLF
            # while git stores LF, so CRLF-only differences are not drift.
            actions.append(MirrorAction("SAME", name))
        else:
            actions.append(MirrorAction("UPDATE", name))
    for name in sorted(existing):
        if name not in source and (prune or name.casefold() in folded_source):
            actions.append(MirrorAction("REMOVE", name))
    return sorted(actions, key=lambda item: (item.name, item.action))


def apply_mirror(
    plan: list[MirrorAction], source: dict[str, bytes], target: Path
) -> None:
    # Remove first: on case-insensitive file systems a case-variant removal must
    # not delete the page that was just written under the new name.
    for item in plan:
        if item.action == "REMOVE":
            (target / item.name).unlink()
    for item in plan:
        if item.action in {"ADD", "UPDATE"}:
            (target / item.name).write_bytes(source[item.name])


def _count(plan: list[MirrorAction], action: str) -> int:
    return sum(1 for item in plan if item.action == action)


def mirror_summary(plan: list[MirrorAction]) -> str:
    pages = sum(1 for item in plan if item.action != "REMOVE")
    return (
        f"wiki mirror: {pages} page(s): {_count(plan, 'ADD')} added, "
        f"{_count(plan, 'UPDATE')} updated, {_count(plan, 'REMOVE')} removed, "
        f"{_count(plan, 'SAME')} unchanged"
    )


def content_digest(source: dict[str, bytes]) -> str:
    digest = hashlib.sha256()
    for name in sorted(source):
        digest.update(name.encode("utf-8") + b"\0")
        digest.update(hashlib.sha256(source[name]).digest())
    return digest.hexdigest()


def commit_message(plan: list[MirrorAction], source: dict[str, bytes]) -> str:
    return (
        "docs: sync wiki from docs/wiki\n\n"
        f"Source: {REPOSITORY_URL}/tree/main/docs/wiki\n"
        f"Pages: {len(source)}\n"
        f"Digest: sha256:{content_digest(source)}\n"
        f"Changes: {_count(plan, 'ADD')} added, {_count(plan, 'UPDATE')} updated, "
        f"{_count(plan, 'REMOVE')} removed\n"
    )


def run_git(checkout: Path, *args: str) -> subprocess.CompletedProcess[str]:
    """Run a local, non-network git command inside ``checkout``."""
    if not args or args[0] not in ALLOWED_GIT_COMMANDS:
        name = args[0] if args else "(none)"
        raise WikiSyncError(f"git {name} is not permitted by wiki_sync")
    # The checkout's own git configuration is deliberately left untouched.
    command = ["git", "-C", str(checkout), *args]
    try:
        return subprocess.run(
            command,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
            check=False,
        )
    except OSError as error:
        raise WikiSyncError(f"cannot run git: {error}") from error


def _git_checked(checkout: Path, *args: str) -> str:
    result = run_git(checkout, *args)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise WikiSyncError(f"git {args[0]} failed: {detail}")
    return result.stdout


def ensure_git_checkout(checkout: Path, *, need_identity: bool) -> None:
    shown = checkout.as_posix()
    result = run_git(checkout, "rev-parse", "--show-toplevel")
    top_level = result.stdout.strip()
    if result.returncode != 0 or not top_level:
        raise TargetError(shown, "target is not a git checkout")
    if not os.path.samefile(top_level, checkout):
        raise TargetError(shown, "target must be the root of the wiki checkout")
    for entry in _git_checked(checkout, "status", "--porcelain").splitlines():
        if not entry.startswith("?? ") or entry.endswith(".md"):
            raise TargetError(
                shown, f"wiki checkout has uncommitted changes ({entry.strip()})"
            )
    if need_identity:
        for variable in ("GIT_AUTHOR_IDENT", "GIT_COMMITTER_IDENT"):
            if run_git(checkout, "var", variable).returncode != 0:
                raise TargetError(
                    shown,
                    "git author identity is not configured for the wiki checkout; "
                    "set user.name and user.email there before publishing",
                )


def stage_and_commit(
    checkout: Path, plan: list[MirrorAction], source: dict[str, bytes]
) -> str | None:
    """Stage exactly the mirrored pages and commit them; return the short SHA."""
    written = [item.name for item in plan if item.action in {"ADD", "UPDATE"}]
    removed = [item.name for item in plan if item.action == "REMOVE"]
    tracked: set[str] = set()
    if removed:
        listing = _git_checked(checkout, "ls-files", "-z", "--", *removed)
        tracked = {entry for entry in listing.split("\0") if entry}
    paths = [*written, *(name for name in removed if name in tracked)]
    if not paths:
        return None
    _git_checked(checkout, "add", "-A", "--", *paths)
    if run_git(checkout, "diff", "--cached", "--quiet").returncode == 0:
        return None
    _git_checked(checkout, "commit", "-m", commit_message(plan, source))
    return _git_checked(checkout, "rev-parse", "--short", "HEAD").strip()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate docs/wiki and mirror it into a local GitHub Wiki checkout."
    )
    commands = parser.add_subparsers(dest="command", required=True)

    def add_source_arguments(command: argparse.ArgumentParser) -> None:
        command.add_argument("--repo-root", type=Path, default=Path("."))
        command.add_argument("--wiki-dir", type=Path, default=Path(DEFAULT_WIKI_DIR))
        command.add_argument(
            "--allow-path",
            action="append",
            default=[],
            metavar="PATH",
            help="repository path (or prefix ending in /) that repo URLs may "
            "reference although it is absent from the working tree; repeatable",
        )

    check = commands.add_parser(
        "check", aliases=["validate"], help="validate the wiki page set"
    )
    add_source_arguments(check)
    check.add_argument("--stats", action="store_true", help="print page/link counts")

    sync = commands.add_parser("sync", help="mirror pages into a directory (no git)")
    add_source_arguments(sync)
    sync.add_argument("--target", type=Path, required=True)
    sync.add_argument("--dry-run", action="store_true", help="print the plan only")
    sync.add_argument("--check", action="store_true", help="exit 1 on any difference")
    sync.add_argument("--no-prune", action="store_true", help="keep extra target pages")

    publish = commands.add_parser(
        "publish", help="mirror pages into a wiki git checkout and commit locally"
    )
    add_source_arguments(publish)
    publish.add_argument(
        "checkout", type=Path, help="local clone of the GitHub Wiki repository"
    )
    publish.add_argument("--dry-run", action="store_true", help="print the plan only")
    return parser


def _resolve_paths(args: argparse.Namespace) -> tuple[Path, Path]:
    repo_root = args.repo_root.resolve()
    wiki_dir = args.wiki_dir
    return repo_root, wiki_dir if wiki_dir.is_absolute() else repo_root / wiki_dir


def _print_plan(plan: list[MirrorAction]) -> None:
    for item in plan:
        print(f"{item.action} {item.name}")
    print(mirror_summary(plan))


def _run_check(args: argparse.Namespace) -> int:
    repo_root, wiki_dir = _resolve_paths(args)
    result = check_wiki(wiki_dir, repo_root, args.allow_path)
    if getattr(args, "stats", False):
        print("\n".join(format_stats(result)))
    if result.problems:
        for problem in result.problems:
            print(problem.render(), file=sys.stderr)
        print(
            f"wiki check failed: {len(result.problems)} problem(s) in "
            f"{result.page_count} page(s)",
            file=sys.stderr,
        )
        return 1
    print(f"wiki check passed: {result.page_count} page(s), 0 problem(s)")
    return 0


def _run_sync(args: argparse.Namespace) -> int:
    repo_root, wiki_dir = _resolve_paths(args)
    ensure_safe_target(args.target, wiki_dir, repo_root)
    source = collect_source_pages(wiki_dir)
    plan = plan_mirror(source, args.target, prune=not args.no_prune)
    _print_plan(plan)
    if args.check:
        return 1 if any(item.action != "SAME" for item in plan) else 0
    if not args.dry_run:
        apply_mirror(plan, source, args.target)
    return 0


def _run_publish(args: argparse.Namespace) -> int:
    repo_root, wiki_dir = _resolve_paths(args)
    ensure_safe_target(args.checkout, wiki_dir, repo_root)
    ensure_git_checkout(args.checkout, need_identity=not args.dry_run)
    source = collect_source_pages(wiki_dir)
    plan = plan_mirror(source, args.checkout, prune=True)
    _print_plan(plan)
    if args.dry_run:
        return 0
    apply_mirror(plan, source, args.checkout)
    commit = stage_and_commit(args.checkout, plan, source)
    if commit is None:
        print("wiki publish: nothing to commit; the checkout is already up to date")
    else:
        print(f"wiki publish: created local commit {commit}; push it manually")
    return 0


def run(args: argparse.Namespace) -> int:
    if args.command in {"check", "validate"}:
        return _run_check(args)
    # Never mirror a page set that fails validation.
    if _run_check(args) != 0:
        return 1
    return _run_sync(args) if args.command == "sync" else _run_publish(args)


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        return run(args)
    except TargetError as error:
        print(error.render(), file=sys.stderr)
        return 2
    except WikiSyncError as error:
        print(f"wiki_sync error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    for _stream in (sys.stdout, sys.stderr):
        _stream.reconfigure(errors="backslashreplace")
    raise SystemExit(main())
