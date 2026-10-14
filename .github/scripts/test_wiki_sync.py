from __future__ import annotations

import contextlib
import io
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import wiki_sync as sync

REPO = "https://github.com/patton174/coco-framework"
FOOTER = (
    f"[Repository]({REPO}) | [Changelog]({REPO}/blob/main/CHANGELOG.md) | "
    f"[Contributing]({REPO}/blob/main/CONTRIBUTING.md) | "
    f"[License]({REPO}/blob/main/LICENSE)\n"
)
GIT_ENV = {
    "GIT_AUTHOR_NAME": "Wiki Test",
    "GIT_AUTHOR_EMAIL": "wiki-test@example.invalid",
    "GIT_COMMITTER_NAME": "Wiki Test",
    "GIT_COMMITTER_EMAIL": "wiki-test@example.invalid",
    "GIT_CONFIG_GLOBAL": os.devnull,
    "GIT_CONFIG_NOSYSTEM": "1",
}


class WikiTestCase(unittest.TestCase):
    """Builds a valid repository fixture; each test breaks one thing."""

    def setUp(self) -> None:
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        self.scratch = Path(scratch.name)
        self.repo = self.scratch / "repo"
        self.wiki = self.repo / "docs" / "wiki"
        self.wiki.mkdir(parents=True)
        for name in ("CHANGELOG.md", "CONTRIBUTING.md", "LICENSE", "pom.xml"):
            (self.repo / name).write_text("fixture\n", encoding="utf-8")
        (self.repo / "docs" / "README.md").write_text("docs\n", encoding="utf-8")
        self.write("Home.md", "# Home\n\nStart with [Page One](Page-One).\n")
        self.write("_Sidebar.md", "- [Home](Home)\n- [One](Page-One)\n")
        self.write("_Footer.md", FOOTER)
        self.write("Page-One.md", "# Page One\n\nContent.\n")

    def write(self, name: str, text: str | bytes) -> Path:
        path = self.wiki / name
        path.write_bytes(text if isinstance(text, bytes) else text.encode("utf-8"))
        return path

    def check(self, allow: tuple[str, ...] = ()) -> sync.CheckResult:
        return sync.check_wiki(self.wiki, self.repo, allow)

    def rules(self) -> set[tuple[str, str]]:
        return {
            (problem.path.removeprefix("docs/wiki/"), problem.rule)
            for problem in self.check().problems
        }

    def assertRule(self, rule: str, path: str | None = None) -> None:
        found = self.rules()
        if path is None:
            self.assertIn(rule, {item[1] for item in found}, found)
        else:
            self.assertIn((path, rule), found)

    def assertClean(self) -> None:
        self.assertEqual((), self.check().problems)

    def run_main(self, *argv: str) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = sync.main(list(argv))
        return code, out.getvalue(), err.getvalue()


class LayoutRuleTests(WikiTestCase):
    def test_minimal_valid_wiki_passes(self) -> None:
        result = self.check()
        self.assertEqual((), result.problems)
        self.assertEqual(4, result.page_count)

    def test_readme_is_excluded_from_every_rule(self) -> None:
        self.write(
            "README.md", "待确认 D:\\x \n[bad](../x.md) ```\nghp_" + "a" * 36 + "\n"
        )
        self.assertClean()
        self.assertNotIn("README.md", self.check().page_stats)

    def test_flat_layout_rejects_subdirectory_other_files_and_hidden_files(
        self,
    ) -> None:
        (self.wiki / "sub").mkdir()
        self.write("image.png", b"\x89PNG")
        self.write(".hidden.md", "# hidden\n")
        found = self.rules()
        self.assertIn(("sub", "W001"), found)
        self.assertIn(("image.png", "W001"), found)
        self.assertIn((".hidden.md", "W001"), found)

    def test_page_names_must_be_ascii_title_case_with_hyphens(self) -> None:
        for bad in ("lower-case.md", "Has_Underscore.md", "页面.md", "Trailing-.md"):
            with self.subTest(bad=bad):
                self.write(bad, "# x\n")
                self.assertRule("W002", bad)
                (self.wiki / bad).unlink()
        for good in ("FAQ-Troubleshooting.md", "Module-Features-Data-And-Security.md"):
            with self.subTest(good=good):
                self.assertTrue(sync.PAGE_NAME_PATTERN.fullmatch(good))

    def test_case_only_duplicates_are_reported(self) -> None:
        checker = sync.WikiChecker(self.wiki, self.repo)
        checker._check_layout(
            ["Home.md", "_Sidebar.md", "_Footer.md", "Page.md", "page.md"]
        )
        self.assertIn(
            ("docs/wiki/page.md", "W002"),
            {(problem.path, problem.rule) for problem in checker.problems},
        )

    def test_special_pages_are_required_with_exact_case(self) -> None:
        for special in sync.SPECIAL_PAGES:
            with self.subTest(special=special):
                backup = (self.wiki / special).read_bytes()
                (self.wiki / special).unlink()
                self.assertRule("W003", special)
                self.write(special, backup)

    def test_encoding_rules(self) -> None:
        cases = {
            "bom": b"\xef\xbb\xbf# Page One\n",
            "crlf": b"# Page One\r\n\r\nBody\r\n",
            "no-newline": b"# Page One",
            "extra-newline": b"# Page One\n\n",
            "invalid-utf8": b"# Page One \xff\n",
            "empty": b"",
        }
        for label, content in cases.items():
            with self.subTest(label=label):
                self.write("Page-One.md", content)
                self.assertRule("W005", "Page-One.md")
        self.write("Page-One.md", "# Page One\n")
        self.assertClean()

    def test_front_matter_is_rejected(self) -> None:
        self.write("Page-One.md", "---\ntitle: x\n---\n# Page One\n")
        self.assertRule("W006", "Page-One.md")


class LinkRuleTests(WikiTestCase):
    def link_page(self, body: str) -> None:
        self.write("Page-One.md", f"# Page One\n\n{body}\n")

    def test_path_style_links_are_rejected(self) -> None:
        cases = (
            "[x](Home.md)",
            "[x](../Home)",
            "[x](./Home)",
            "[x](dir/Home)",
            "[x](dir\\Home)",
            "[x](C:\\Users\\a\\b)",
            "[x](file:///tmp/x)",
            "[x]()",
            '<a href="Home">x</a>',
            "[ref]\n\n[ref]: Home.md",
        )
        for body in cases:
            with self.subTest(body=body):
                self.link_page(body)
                self.assertRule("W010", "Page-One.md")

    def test_external_and_wiki_links_are_accepted(self) -> None:
        self.link_page(
            "[a](https://example.com/x) [c](Home) [d](Home 'Title') [e](Home#home)\n"
            "[[Home]] [[Go home|Home]] [[Page One]] ![img](https://example.com/a.png)\n"
            '<https://example.com> <a href="https://example.com">x</a>'
        )
        self.assertClean()

    def test_unknown_page_is_reported_with_case_hint(self) -> None:
        self.link_page("[x](Missing-Page) [y](page-one) [[Nope]]")
        problems = [p for p in self.check().problems if p.rule == "W011"]
        self.assertEqual(3, len(problems))
        self.assertTrue(any("did you mean 'Page-One'" in p.message for p in problems))

    def test_navigation_pages_are_not_link_targets(self) -> None:
        self.link_page("[x](_Sidebar)")
        self.assertRule("W011", "Page-One.md")

    def test_anchors_use_github_slugs(self) -> None:
        self.write(
            "Page-One.md",
            "# Page One\n\n## 快速开始：构建与测试\n\n## Repeat\n\n## Repeat\n\n"
            "## `Code` *Heading* (Beta)\n\n"
            "[a](#快速开始构建与测试) [b](#repeat) [c](#repeat-1) [d](#code-heading-beta)\n"
            "[e](Home#home) [f](#missing) [g](Home#nope)\n",
        )
        messages = [p.message for p in self.check().problems if p.rule == "W012"]
        self.assertEqual(2, len(messages), messages)
        self.assertTrue(any("#missing" in m for m in messages))
        self.assertTrue(any("#nope" in m for m in messages))

    def test_links_inside_code_are_ignored(self) -> None:
        self.link_page(
            "```text\n[bad](../x.md) [[Nope]] https://github.com/patton174/coco-framework/blob/main/nope\n```\n"
            "~~~\n[bad](../y.md)\n~~~\n"
            "Inline `[bad](../z.md)` and ``[[Nope]]`` stay ignored.\n"
            "<!-- [bad](../hidden.md) -->"
        )
        self.assertClean()

    def test_repository_urls_must_exist_with_exact_case_and_kind(self) -> None:
        (self.repo / "docs" / "guide").mkdir()
        (self.repo / "docs" / "guide" / "Intro.md").write_text("x\n", encoding="utf-8")
        good = (
            f"[a]({REPO}/blob/main/docs/guide/Intro.md) [b]({REPO}/tree/main/docs/guide)\n"
            f"[c]({REPO}/blob/main/docs/guide/Intro.md#section) [d]({REPO}/tree/main)\n"
            f"Plain {REPO}/blob/main/CHANGELOG.md. And ({REPO}/blob/main/LICENSE)\n"
        )
        self.link_page(good.rstrip("\n"))
        self.assertClean()
        bad = {
            "missing file": f"{REPO}/blob/main/docs/guide/Nope.md",
            "case mismatch": f"{REPO}/blob/main/docs/guide/intro.md",
            "case mismatch dir": f"{REPO}/blob/main/Docs/guide/Intro.md",
            "blob is dir": f"{REPO}/blob/main/docs/guide",
            "tree is file": f"{REPO}/tree/main/docs/guide/Intro.md",
            "through file": f"{REPO}/blob/main/LICENSE/more",
            "agents dir": f"{REPO}/blob/main/.agents/tasks/x.md",
            "dotdot": f"{REPO}/blob/main/docs/../LICENSE",
            "blob root": f"{REPO}/blob/main",
        }
        for label, url in bad.items():
            with self.subTest(label=label):
                self.link_page(f"[x]({url})")
                self.assertRule("W013", "Page-One.md")

    def test_other_repository_urls_are_external(self) -> None:
        self.link_page(
            f"[a]({REPO}/issues/1) [b]({REPO}/releases/tag/v1.0.0) "
            f"[c]({REPO}/compare/v1...v2) [d]({REPO}/blob/dev/nope.md)"
        )
        self.assertClean()

    def test_allow_list_tolerates_documented_paths(self) -> None:
        self.link_page(
            f"[a]({REPO}/blob/main/docs/Future.md) [b]({REPO}/tree/main/future/dir)"
        )
        self.assertRule("W013", "Page-One.md")
        allowed = self.check(("docs/Future.md", "future/"))
        self.assertEqual((), allowed.problems)
        self.assertEqual((), sync.REPO_PATH_ALLOWLIST)


class StructureRuleTests(WikiTestCase):
    def test_fences_must_be_balanced(self) -> None:
        self.write("Page-One.md", "# Page One\n\n```text\nopen\n")
        problem = next(p for p in self.check().problems if p.rule == "W020")
        self.assertEqual(3, problem.line)
        self.write(
            "Page-One.md", "# Page One\n\n````md\n```text\n```\n````\n~~~\nx\n~~~\n"
        )
        self.assertClean()
        self.write("Page-One.md", "# Page One\n\n```text\n~~~\n")
        self.assertRule("W020", "Page-One.md")

    def test_mermaid_fences(self) -> None:
        valid = (
            "flowchart LR\n  A --> B\n",
            "graph TD\n  A --> B\n",
            "%%{init: {}}%%\nsequenceDiagram\n  A->>B: hi\n",
            "---\ntitle: T\n---\nstateDiagram-v2\n  [*] --> A\n",
        )
        for body in valid:
            with self.subTest(body=body):
                self.write("Page-One.md", f"# Page One\n\n```mermaid\n{body}```\n")
                self.assertClean()
        self.write("Page-One.md", "# Page One\n\n```mermaid\n\n```\n")
        self.assertRule("W021", "Page-One.md")
        self.write("Page-One.md", "# Page One\n\n```mermaid\nA --> B\n```\n")
        self.assertRule("W021", "Page-One.md")

    def test_hygiene_rules_each_have_a_hit(self) -> None:
        samples = {
            "unresolved-marker": "待确认",
            "absolute-local-path": "D:\\Programs\\Java",
            "unix-home-path": "/Users/alice/work",
            "local-file-url": "file:///tmp/x",
            "local-jdk-path": "jdk_1.8",
            "machine-specific-wording": "本机",
            "internal-commit-reference": "12515b4",
            "jvm-crash-log-name": "hs_err_pid123.log",
            "draft-area-path": ".agents/tasks",
            "plan-directory": "docs/wiki/_plan/",
            "uncommitted-changes": "未提交的改动",
            "email-address": "someone@example.com",
            "legacy-wiki-path": "modules/web.md",
        }
        self.assertEqual({label for label, _ in sync.HYGIENE_RULES}, set(samples))
        for label, sample in samples.items():
            with self.subTest(label=label):
                self.write("Page-One.md", f"# Page One\n\nBefore {sample} after\n")
                problems = [p for p in self.check().problems if p.rule == "W030"]
                self.assertTrue(any(label in p.message for p in problems), problems)
                self.assertEqual(3, problems[0].line)

    def test_hygiene_scans_code_fences_too(self) -> None:
        self.write(
            "Page-One.md", "# Page One\n\n```powershell\n$x = 'C:\\Users\\a'\n```\n"
        )
        self.assertRule("W030", "Page-One.md")

    def test_hygiene_does_not_flag_placeholders(self) -> None:
        self.write(
            "Page-One.md",
            "# Page One\n\n```powershell\n$env:JAVA_HOME='<JDK21_HOME>'\n$env:Path=\"$env:JAVA_HOME\\bin;$env:Path\"\n```\n"
            f"Use {REPO}/blob/main/LICENSE and `COCO_AGENT_APP_PRIVATE_KEY`.\n",
        )
        self.assertClean()

    def test_secret_shapes(self) -> None:
        samples = (
            "ghp_" + "A" * 36,
            "github_pat_" + "a1" * 12,
            "AKIA" + "ABCDEFGH12345678",
            "-----BEGIN RSA PRIVATE KEY-----",
            "xoxb-1234567890-abc",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlMTIzNDU2",
        )
        for sample in samples:
            with self.subTest(sample=sample[:12]):
                self.write("Page-One.md", f"# Page One\n\n```text\n{sample}\n```\n")
                found = [p for p in self.check().problems if p.rule == "W031"]
                self.assertEqual(1, len(found), found)
                self.assertEqual(4, found[0].line)

    def test_sidebar_must_link_every_content_page(self) -> None:
        self.write("Extra-Page.md", "# Extra\n")
        self.write("Home.md", "# Home\n\n[One](Page-One) [Extra](Extra-Page)\n")
        self.assertRule("W040", "_Sidebar.md")
        self.write("_Sidebar.md", "[Home](Home) [One](Page-One) [Extra](Extra-Page)\n")
        self.assertClean()

    def test_footer_must_link_repository_changelog_contributing_and_license(
        self,
    ) -> None:
        for missing in (f"{REPO}/blob/main/LICENSE", f"{REPO}/blob/main/CHANGELOG.md"):
            with self.subTest(missing=missing):
                self.write(
                    "_Footer.md", FOOTER.replace(missing, f"{REPO}/blob/main/pom.xml")
                )
                self.assertRule("W041", "_Footer.md")
        self.write("_Footer.md", FOOTER.replace(f"({REPO})", "(https://example.com)"))
        self.assertRule("W041", "_Footer.md")
        self.write("_Footer.md", FOOTER.replace(f"({REPO})", f"({REPO}/)"))
        self.assertClean()

    def test_every_page_must_be_reachable_from_home(self) -> None:
        self.write("Orphan-Page.md", "# Orphan\n")
        self.write(
            "_Sidebar.md", "[Home](Home) [One](Page-One) [Orphan](Orphan-Page)\n"
        )
        self.assertRule("W042", "Home.md")
        self.write("Page-One.md", "# Page One\n\nNext: [Orphan](Orphan-Page)\n")
        self.assertClean()

    def test_problems_are_sorted_and_stable(self) -> None:
        self.write("Page-One.md", "# Page One\n\n[b](Nope-B)\n\n[a](Nope-A)\n待确认\n")
        self.write("Another-Page.md", "# A\n\n[x](Nope)\n")
        first, second = self.check().problems, self.check().problems
        self.assertEqual(first, second)
        self.assertEqual(tuple(sorted(first)), first)
        rendered = [p.render() for p in first]
        self.assertTrue(all(r.startswith("docs/wiki/") for r in rendered))
        self.assertTrue(
            any(r.startswith("docs/wiki/Page-One.md:3: W011:") for r in rendered)
        )

    def test_stats_report_counts(self) -> None:
        self.write(
            "Page-One.md",
            f"# Page One\n\n[h](Home) [e](https://example.com) [r]({REPO}/blob/main/LICENSE)\n"
            "```mermaid\nflowchart LR\n A-->B\n```\n",
        )
        stats = self.check().page_stats["Page-One.md"]
        self.assertEqual(
            (1, 1, 1, 1, 1),
            (
                stats.internal_links,
                stats.repository_links,
                stats.external_links,
                stats.mermaid_fences,
                stats.headings,
            ),
        )
        lines = sync.format_stats(self.check())
        self.assertEqual("stats: pages=4", lines[0])
        self.assertEqual(sorted(lines[2:]), lines[2:])


class CommandLineTests(WikiTestCase):
    def test_check_exit_codes_and_messages(self) -> None:
        code, out, err = self.run_main("check", "--repo-root", str(self.repo))
        self.assertEqual((0, ""), (code, err))
        self.assertIn("wiki check passed: 4 page(s)", out)
        self.write("Page-One.md", "# Page One\n\n[x](Gone)\n")
        code, _, err = self.run_main("validate", "--repo-root", str(self.repo))
        self.assertEqual(1, code)
        self.assertIn("docs/wiki/Page-One.md:3: W011:", err)
        code, _, err = self.run_main("check", "--repo-root", str(self.scratch))
        self.assertEqual(2, code)
        self.assertIn("wiki directory not found", err)

    def test_stats_flag_prints_before_the_verdict(self) -> None:
        code, out, _ = self.run_main("check", "--stats", "--repo-root", str(self.repo))
        self.assertEqual(0, code)
        self.assertTrue(out.startswith("stats: pages=4\n"))

    def test_allow_path_option(self) -> None:
        self.write("Page-One.md", f"# Page One\n\n[x]({REPO}/blob/main/docs/Soon.md)\n")
        args = ["check", "--repo-root", str(self.repo)]
        self.assertEqual(1, self.run_main(*args)[0])
        self.assertEqual(0, self.run_main(*args, "--allow-path", "docs/Soon.md")[0])

    def test_publish_requires_a_checkout_argument(self) -> None:
        with (
            contextlib.redirect_stderr(io.StringIO()),
            self.assertRaises(SystemExit) as raised,
        ):
            sync.main(["publish"])
        self.assertEqual(2, raised.exception.code)


class MirrorTests(WikiTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.target = self.scratch / "wiki-clone"
        self.target.mkdir()

    def sync_args(self, *extra: str) -> list[str]:
        return [
            "sync",
            "--repo-root",
            str(self.repo),
            "--target",
            str(self.target),
            *extra,
        ]

    def listing(self) -> list[str]:
        return sorted(os.listdir(self.target))

    def test_sync_classifies_add_update_remove_same(self) -> None:
        (self.target / "Page-One.md").write_bytes(b"old\n")
        (self.target / "Home.md").write_bytes((self.wiki / "Home.md").read_bytes())
        (self.target / "Stale.md").write_bytes(b"stale\n")
        code, out, _ = self.run_main(*self.sync_args())
        self.assertEqual(0, code)
        self.assertEqual(
            [
                "SAME Home.md",
                "UPDATE Page-One.md",
                "REMOVE Stale.md",
                "ADD _Footer.md",
                "ADD _Sidebar.md",
            ],
            out.splitlines()[1:-1],
        )
        self.assertEqual(
            "wiki mirror: 4 page(s): 2 added, 1 updated, 1 removed, 1 unchanged",
            out.splitlines()[-1],
        )
        self.assertEqual(
            ["Home.md", "Page-One.md", "_Footer.md", "_Sidebar.md"], self.listing()
        )
        self.assertEqual(
            (self.wiki / "Page-One.md").read_bytes(),
            (self.target / "Page-One.md").read_bytes(),
        )

    def test_second_run_is_a_noop_and_output_is_deterministic(self) -> None:
        self.run_main(*self.sync_args())
        first = self.run_main(*self.sync_args())
        second = self.run_main(*self.sync_args())
        self.assertEqual(first, second)
        self.assertNotIn("ADD", first[1])
        self.assertNotIn("UPDATE", first[1])
        self.assertEqual(0, self.run_main(*self.sync_args("--check"))[0])

    def test_readme_is_never_mirrored(self) -> None:
        self.write("README.md", "# Repo-only note\n")
        self.run_main(*self.sync_args())
        self.assertNotIn("README.md", self.listing())

    def test_crlf_checkout_of_identical_content_is_not_drift(self) -> None:
        for name, content in sync.collect_source_pages(self.wiki).items():
            (self.target / name).write_bytes(content.replace(b"\n", b"\r\n"))
        code, out, _ = self.run_main(*self.sync_args("--check"))
        self.assertEqual(0, code, out)
        self.assertNotIn("UPDATE", out)

    def test_check_flag_reports_drift_without_writing(self) -> None:
        code, out, _ = self.run_main(*self.sync_args("--check"))
        self.assertEqual(1, code)
        self.assertIn("ADD Home.md", out)
        self.assertEqual([], self.listing())

    def test_dry_run_does_not_write(self) -> None:
        code, out, _ = self.run_main(*self.sync_args("--dry-run"))
        self.assertEqual(0, code)
        self.assertIn("ADD Home.md", out)
        self.assertEqual([], self.listing())

    def test_no_prune_keeps_extra_pages(self) -> None:
        (self.target / "Stale.md").write_bytes(b"stale\n")
        self.run_main(*self.sync_args("--no-prune"))
        self.assertIn("Stale.md", self.listing())
        self.run_main(*self.sync_args())
        self.assertNotIn("Stale.md", self.listing())

    def test_git_metadata_other_files_and_directories_are_untouched(self) -> None:
        (self.target / ".git").mkdir()
        (self.target / ".git" / "HEAD").write_text(
            "ref: refs/heads/master\n", encoding="utf-8"
        )
        (self.target / "image.png").write_bytes(b"png")
        (self.target / "assets").mkdir()
        (self.target / "assets" / "Nested.md").write_text("keep\n", encoding="utf-8")
        self.run_main(*self.sync_args())
        self.assertEqual(
            "ref: refs/heads/master\n",
            (self.target / ".git" / "HEAD").read_text("utf-8"),
        )
        self.assertTrue((self.target / "image.png").is_file())
        self.assertTrue((self.target / "assets" / "Nested.md").is_file())

    def test_bytes_are_copied_verbatim(self) -> None:
        body = "# Page One\n\n中文 and `code`\n".encode()
        self.write("Page-One.md", body)
        self.run_main(*self.sync_args())
        self.assertEqual(body, (self.target / "Page-One.md").read_bytes())

    def test_invalid_wiki_is_never_mirrored(self) -> None:
        self.write("Page-One.md", "# Page One\n\n[x](Gone)\n")
        code, _, err = self.run_main(*self.sync_args())
        self.assertEqual(1, code)
        self.assertIn("W011", err)
        self.assertEqual([], self.listing())

    def test_unsafe_targets_are_rejected(self) -> None:
        unsafe = {
            "missing": self.scratch / "does-not-exist",
            "source": self.wiki,
            "parent of source": self.repo / "docs",
            "repo root": self.repo,
            "parent of repo": self.scratch,
        }
        for label, target in unsafe.items():
            with self.subTest(label=label):
                code, _, err = self.run_main(
                    "sync", "--repo-root", str(self.repo), "--target", str(target)
                )
                self.assertEqual(2, code)
                self.assertIn("W050", err)
        lookalike = self.scratch / "lookalike"
        lookalike.mkdir()
        (lookalike / "AGENTS.md").write_text("x\n", encoding="utf-8")
        code, _, err = self.run_main(
            "sync", "--repo-root", str(self.repo), "--target", str(lookalike)
        )
        self.assertEqual(2, code)
        self.assertIn("looks like the main repository", err)


@unittest.skipUnless(shutil.which("git"), "git is required")
class PublishTests(WikiTestCase):
    def setUp(self) -> None:
        super().setUp()
        patcher = mock.patch.dict(os.environ, GIT_ENV)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.checkout = self.scratch / "wiki-checkout"
        self.checkout.mkdir()
        self.git("init", "--quiet")

    def git(self, *args: str, cwd: Path | None = None) -> str:
        done = subprocess.run(
            ["git", *args],
            cwd=cwd or self.checkout,
            capture_output=True,
            text=True,
            encoding="utf-8",
            check=True,
        )
        return done.stdout

    def publish(self, *extra: str) -> tuple[int, str, str]:
        return self.run_main(
            "publish", "--repo-root", str(self.repo), *extra, str(self.checkout)
        )

    def commits(self) -> list[str]:
        done = subprocess.run(
            ["git", "log", "--format=%H"],
            cwd=self.checkout,
            capture_output=True,
            text=True,
            check=False,
        )
        return done.stdout.split()

    def test_publish_creates_one_deterministic_local_commit(self) -> None:
        code, out, err = self.publish()
        self.assertEqual((0, ""), (code, err))
        self.assertIn("created local commit", out)
        self.assertEqual(1, len(self.commits()))
        message = self.git("log", "-1", "--format=%B").strip().splitlines()
        self.assertEqual("docs: sync wiki from docs/wiki", message[0])
        self.assertIn("Pages: 4", message)
        self.assertIn("Changes: 4 added, 0 updated, 0 removed", message)
        self.assertTrue(any(line.startswith("Digest: sha256:") for line in message))
        tracked = self.git("ls-files").split()
        self.assertEqual(
            ["Home.md", "Page-One.md", "_Footer.md", "_Sidebar.md"], sorted(tracked)
        )
        self.assertEqual("", self.git("status", "--porcelain"))

    def test_publish_twice_yields_no_diff_and_no_second_commit(self) -> None:
        self.publish()
        head = self.commits()
        code, out, _ = self.publish()
        self.assertEqual(0, code)
        self.assertIn("nothing to commit", out)
        self.assertEqual(head, self.commits())
        self.assertEqual("", self.git("status", "--porcelain"))

    def test_same_content_gives_the_same_message_in_two_checkouts(self) -> None:
        self.publish()
        other = self.scratch / "other-checkout"
        other.mkdir()
        self.git("init", "--quiet", cwd=other)
        self.run_main("publish", "--repo-root", str(self.repo), str(other))
        first = self.git("log", "-1", "--format=%B")
        self.assertEqual(first, self.git("log", "-1", "--format=%B", cwd=other))

    def test_publish_updates_and_removes_only_wiki_pages(self) -> None:
        self.publish()
        (self.checkout / "notes.txt").write_text("keep me\n", encoding="utf-8")
        self.write("Page-One.md", "# Page One\n\nChanged.\n")
        self.write("Added-Page.md", "# Added\n")
        self.write("Home.md", "# Home\n\n[One](Page-One) [Added](Added-Page)\n")
        self.write("_Sidebar.md", "[Home](Home) [One](Page-One) [Added](Added-Page)\n")
        (self.wiki / "README.md").write_text("repo only\n", encoding="utf-8")
        self.assertEqual(0, self.publish()[0])
        status = dict(
            line.split("\t")[::-1]
            for line in self.git(
                "show", "--name-status", "--format=", "HEAD"
            ).splitlines()
        )
        self.assertEqual(
            {
                "Added-Page.md": "A",
                "Home.md": "M",
                "Page-One.md": "M",
                "_Sidebar.md": "M",
            },
            status,
        )
        self.assertTrue((self.checkout / "notes.txt").is_file())
        self.assertFalse((self.checkout / "README.md").exists())

        (self.wiki / "Added-Page.md").unlink()
        self.write("Home.md", "# Home\n\n[One](Page-One)\n")
        self.write("_Sidebar.md", "[Home](Home) [One](Page-One)\n")
        self.assertEqual(0, self.publish()[0])
        removed = self.git("show", "--name-status", "--format=", "HEAD").splitlines()
        self.assertIn("D\tAdded-Page.md", removed)
        self.assertFalse((self.checkout / "Added-Page.md").exists())

    def test_publish_dry_run_changes_nothing(self) -> None:
        code, out, _ = self.publish("--dry-run")
        self.assertEqual(0, code)
        self.assertIn("ADD Home.md", out)
        self.assertEqual([], [n for n in os.listdir(self.checkout) if n != ".git"])
        self.assertEqual([], self.commits())

    def test_publish_refuses_invalid_wiki_without_touching_the_checkout(self) -> None:
        self.write("Page-One.md", "# Page One\n\n[x](Gone)\n")
        code, _, err = self.publish()
        self.assertEqual(1, code)
        self.assertIn("W011", err)
        self.assertEqual([], [n for n in os.listdir(self.checkout) if n != ".git"])

    def test_publish_refuses_non_git_dirty_or_unsafe_targets(self) -> None:
        plain = self.scratch / "plain"
        plain.mkdir()
        code, _, err = self.run_main(
            "publish", "--repo-root", str(self.repo), str(plain)
        )
        self.assertEqual((2, True), (code, "not a git checkout" in err))

        subdir = self.checkout / "nested"
        subdir.mkdir()
        code, _, err = self.run_main(
            "publish", "--repo-root", str(self.repo), str(subdir)
        )
        self.assertEqual(2, code)
        self.assertIn("W050", err)

        self.publish()
        (self.checkout / "Home.md").write_text("local edit\n", encoding="utf-8")
        code, _, err = self.publish()
        self.assertEqual((2, True), (code, "uncommitted changes" in err))
        (self.checkout / "Home.md").write_bytes((self.wiki / "Home.md").read_bytes())
        (self.checkout / "Draft.md").write_text("untracked page\n", encoding="utf-8")
        code, _, err = self.publish()
        self.assertEqual((2, True), (code, "uncommitted changes" in err))

        code, _, err = self.run_main(
            "publish", "--repo-root", str(self.repo), str(self.repo)
        )
        self.assertEqual(2, code)
        self.assertIn("W050", err)

    def test_publish_requires_a_git_identity_before_writing(self) -> None:
        self.git("config", "user.useConfigOnly", "true")
        env = {
            k: v
            for k, v in os.environ.items()
            if k not in GIT_ENV or k.startswith("GIT_CONFIG")
        }
        with mock.patch.dict(os.environ, env, clear=True):
            code, _, err = self.publish()
        self.assertEqual(2, code)
        self.assertIn("identity", err)
        self.assertEqual([], [n for n in os.listdir(self.checkout) if n != ".git"])

    def test_git_wrapper_can_never_push_or_touch_the_network(self) -> None:
        for forbidden in (
            "push",
            "fetch",
            "pull",
            "clone",
            "remote",
            "config",
            "reset",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, sync.ALLOWED_GIT_COMMANDS)
                with self.assertRaises(sync.WikiSyncError):
                    sync.run_git(self.checkout, forbidden)
        with self.assertRaises(sync.WikiSyncError):
            sync.run_git(self.checkout)

    def test_publish_never_invokes_git_push(self) -> None:
        real_run = subprocess.run
        seen: list[list[str]] = []

        def recording(command: list[str], *args: object, **kwargs: object):
            seen.append(command)
            return real_run(command, *args, **kwargs)

        with mock.patch.object(sync.subprocess, "run", recording):
            self.assertEqual(0, self.publish()[0])
        self.assertTrue(seen)
        subcommands = {
            command[command.index("-C") + 2] for command in seen if "-C" in command
        }
        self.assertTrue(subcommands <= sync.ALLOWED_GIT_COMMANDS, subcommands)
        self.assertNotIn("push", subcommands)


if __name__ == "__main__":
    unittest.main()
