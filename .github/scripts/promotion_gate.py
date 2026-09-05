#!/usr/bin/env python3
"""Promotion gate for releasing the integration branch to the release branch.

This gate has a different job from the contributor gate. Content was already
judged when it entered `dev`: full CI, the review jury, and the issue gate all
ran on the contributor pull request that introduced it. Re-judging it here would
be redundant, so this gate verifies *authority and batch integrity* only:

1. Head must be exactly the integration branch. Any other branch proposing to
   the release branch is rejected, including one opened by the owner.
2. The pull request author must be the repository owner.
3. The integration branch must contain every release-branch commit, so nothing
   on the release branch is silently dropped by the promotion.
4. No bound agent-review issue may still be open.

It deliberately does NOT require the per-change gates to be present on the
integration head. `agent-review.yml` runs on `pull_request_target`, so the jury
and issue gate publish against the contributor pull request head, never against
the merge commit that lands on `dev`. Requiring them on the `dev` head would
therefore never be satisfiable. Fresh verification of the promotion itself comes
from `CI gate`, which `ci.yml` runs on the promotion pull request.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path
from typing import Any

from agent_review import (
    DEFAULT_BRANCH,
    FINDING_ISSUE_LABEL,
    INTEGRATION_BRANCH,
    PROMOTION_STATUS_CONTEXT,
    SHA_RE,
    GitHubClient,
    ReviewError,
    parse_finding_issue_marker,
    publish_status,
    read_json,
    require_repository,
)


def require_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReviewError(f"{label} must be a JSON object.")
    return value


def resolve_pull_request(event: dict[str, Any], repository_id: int) -> dict[str, Any]:
    """Bind the promotion pull request, or report that this gate does not apply."""

    pull_request = event.get("pull_request")
    if not isinstance(pull_request, dict):
        raise ReviewError("Promotion gate requires a pull request event.")
    base = require_mapping(pull_request.get("base"), "Pull request base")
    head = require_mapping(pull_request.get("head"), "Pull request head")
    # A dev-targeting pull request is the contributor gate's responsibility.
    if str(base.get("ref") or "") != DEFAULT_BRANCH:
        return {"applies": False}
    head_sha = str(head.get("sha") or "")
    if not SHA_RE.fullmatch(head_sha):
        raise ReviewError("Pull request head SHA is invalid.")
    head_repository = require_mapping(head.get("repo"), "Pull request head repository")
    if head_repository.get("id") != repository_id:
        raise ReviewError(
            "A promotion pull request must originate from this repository."
        )
    number = pull_request.get("number")
    if type(number) is not int or number < 1:
        raise ReviewError("Pull request number is invalid.")
    return {
        "applies": True,
        "head_ref": str(head.get("ref") or ""),
        "head_sha": head_sha,
        "number": number,
        "pull_request": pull_request,
    }


def require_promotion_source(head_ref: str) -> None:
    """Only the integration branch may propose a release."""

    if head_ref != INTEGRATION_BRANCH:
        raise ReviewError(
            f"Only '{INTEGRATION_BRANCH}' may be promoted to "
            f"'{DEFAULT_BRANCH}'; head is '{head_ref}'."
        )


def require_owner_author(pull_request: dict[str, Any], repository_owner: str) -> str:
    """Only the repository owner may promote a release."""

    user = require_mapping(pull_request.get("user"), "Pull request author")
    login = str(user.get("login") or "")
    if not login:
        raise ReviewError("Pull request author identity is invalid.")
    if login.lower() != repository_owner.lower():
        raise ReviewError(
            f"Only the repository owner '{repository_owner}' may promote to "
            f"'{DEFAULT_BRANCH}'; author is '{login}'."
        )
    return login


def require_no_release_commits_dropped(
    client: GitHubClient, repository: str, head_sha: str
) -> None:
    """The integration branch must contain every release-branch commit.

    Merge-commit promotion keeps this satisfiable: the release branch never gains
    a commit the integration branch lacks. A non-zero behind_by therefore means
    someone committed directly to the release branch, and promoting would
    silently drop that work.
    """

    comparison = client.get_json(
        f"repos/{repository}/compare/{DEFAULT_BRANCH}...{head_sha}"
    )
    if not isinstance(comparison, dict):
        raise ReviewError("GitHub branch comparison response is invalid.")
    behind_by = comparison.get("behind_by")
    if type(behind_by) is not int or behind_by < 0:
        raise ReviewError("GitHub branch comparison behind_by is invalid.")
    if behind_by > 0:
        raise ReviewError(
            f"'{INTEGRATION_BRANCH}' is missing {behind_by} commit(s) from "
            f"'{DEFAULT_BRANCH}'; merge '{DEFAULT_BRANCH}' into "
            f"'{INTEGRATION_BRANCH}' first."
        )


def require_no_open_findings(client: GitHubClient, repository: str) -> None:
    """No bound agent-review finding may still be open at promotion time.

    Deliberately not scoped to one pull request: a finding raised against any
    contributor pull request still blocks the release that would carry it.
    """

    open_findings: list[int] = []
    for issue in client.paginate(
        f"repos/{repository}/issues?state=open&labels={FINDING_ISSUE_LABEL}"
        "&sort=created&direction=asc"
    ):
        if not isinstance(issue, dict):
            continue
        marker = parse_finding_issue_marker(issue.get("body"))
        if marker is None:
            continue
        number = issue.get("number")
        if type(number) is int and number > 0:
            open_findings.append(number)
    if open_findings:
        raise ReviewError(
            "Open Agent review finding(s) must be resolved before promotion: "
            + ", ".join(f"#{number}" for number in sorted(open_findings))
        )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--repository-id", required=True)
    parser.add_argument("--repository-owner", required=True)
    parser.add_argument("--event-path", required=True)
    parser.add_argument("--run-url", required=True)
    args = parser.parse_args(argv)

    repository = require_repository(args.repository)
    if not str(args.repository_id).isdigit():
        raise ReviewError("Repository id is invalid.")
    repository_id = int(args.repository_id)
    repository_owner = str(args.repository_owner or "")
    if not repository_owner:
        raise ReviewError("Repository owner is required.")
    event = read_json(Path(args.event_path))
    if not isinstance(event, dict):
        raise ReviewError("Event payload must be a JSON object.")

    binding = resolve_pull_request(event, repository_id)
    if not binding["applies"]:
        print("Promotion gate does not apply to this base branch; skipping.")
        return 0

    head_sha = str(binding["head_sha"])
    client = GitHubClient(os.environ.get("GH_TOKEN", ""))
    try:
        require_promotion_source(str(binding["head_ref"]))
        author = require_owner_author(binding["pull_request"], repository_owner)
        require_no_release_commits_dropped(client, repository, head_sha)
        require_no_open_findings(client, repository)
    except ReviewError as exc:
        publish_status(
            client,
            repository,
            head_sha,
            "failure",
            str(exc),
            args.run_url,
            context=PROMOTION_STATUS_CONTEXT,
        )
        print(f"::error::{exc}")
        return 1

    publish_status(
        client,
        repository,
        head_sha,
        "success",
        f"{INTEGRATION_BRANCH} promotion authorised for {author}; "
        f"no {DEFAULT_BRANCH} commits dropped and no open findings.",
        args.run_url,
        context=PROMOTION_STATUS_CONTEXT,
    )
    print(f"Promotion gate passed for {author}.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except ReviewError as error:
        print(f"::error::{error}")
        sys.exit(1)
