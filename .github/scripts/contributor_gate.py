#!/usr/bin/env python3
"""Admission gate for pull requests targeting the integration branch.

Every contributor change enters through `dev-<login>` -> `dev`. This gate answers
one question: is this change *allowed to be proposed at all*? It never inspects
the diff -- that is the job of CI and the review jury, which run unchanged on the
same pull request.

Checks, in order, all fail-closed:

1. Base must be the integration branch (a release-branch pull request is skipped
   here and handled by the promotion gate instead).
2. Head must live in this repository, never a fork.
3. Branch name must match `dev-<login>` or an exempt prefix.
4. An exempt prefix additionally requires the exact upstream bot identity, so a
   spoofed `dependabot/` branch from anyone else is still rejected.
5. For `dev-<login>`, the suffix must be the pull request author, so one
   contributor cannot open work under another's name.
6. The author must be a repository collaborator with write access or an
   allow-listed project bot.
"""

from __future__ import annotations

import argparse
import os
import sys
import urllib.parse
from pathlib import Path
from typing import Any

from agent_review import (
    CONTRIBUTOR_BRANCH_RE,
    CONTRIBUTOR_STATUS_CONTEXT,
    EXEMPT_BRANCH_PREFIXES,
    INTEGRATION_BRANCH,
    SHA_RE,
    GitHubClient,
    ReviewError,
    publish_status,
    read_json,
    require_repository,
)


def require_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReviewError(f"{label} must be a JSON object.")
    return value


def allowed_bot_identities(raw: str) -> dict[str, int]:
    """Parse the allow-listed bot identities from `login=bot_id` pairs.

    Kept as configuration rather than a hardcoded list so adding a project bot
    does not require a code change to this protected gate.
    """

    identities: dict[str, int] = {}
    for entry in (raw or "").split(","):
        item = entry.strip()
        if not item:
            continue
        login, separator, bot_id = item.partition("=")
        login = login.strip()
        bot_id = bot_id.strip()
        if not separator or not login or not bot_id.isdigit() or int(bot_id) < 1:
            raise ReviewError(
                "Allowed bot identities must be a comma-separated list of "
                "login=bot_id pairs."
            )
        identities[login.lower()] = int(bot_id)
    return identities


def author_identity(pull_request: dict[str, Any]) -> tuple[str, str, int]:
    """Return the pull request author login, type, and numeric id."""

    user = require_mapping(pull_request.get("user"), "Pull request author")
    login = str(user.get("login") or "")
    user_type = str(user.get("type") or "")
    user_id = user.get("id")
    if not login or type(user_id) is not int or user_id < 1:
        raise ReviewError("Pull request author identity is invalid.")
    return login, user_type, user_id


def exempt_prefix(branch: str) -> str | None:
    for prefix in EXEMPT_BRANCH_PREFIXES:
        if branch.startswith(prefix) and len(branch) > len(prefix):
            return prefix
    return None


def evaluate_branch_naming(
    branch: str, author_login: str, author_bot_id: int, allowed_bots: dict[str, int]
) -> None:
    """Enforce the naming contract, including the identity behind an exemption."""

    prefix = exempt_prefix(branch)
    if prefix is not None:
        # A prefix alone proves nothing: require the exact upstream bot identity
        # so an ordinary account cannot borrow the exemption by naming a branch
        # `dependabot/anything`.
        expected_login = prefix.rstrip("/") + "[bot]"
        expected_bot_id = allowed_bots.get(expected_login.lower())
        if expected_bot_id is None:
            raise ReviewError(
                f"Branch prefix '{prefix}' is exempt but '{expected_login}' is not "
                "an allow-listed bot identity."
            )
        if author_login.lower() != expected_login.lower():
            raise ReviewError(
                f"Branch prefix '{prefix}' is reserved for {expected_login}; "
                f"author is '{author_login}'."
            )
        if author_bot_id != expected_bot_id:
            raise ReviewError(
                f"Author '{author_login}' does not match the allow-listed "
                f"{expected_login} user id."
            )
        return

    if CONTRIBUTOR_BRANCH_RE.fullmatch(branch) is None:
        raise ReviewError(
            f"Branch '{branch}' must be named dev-<your-login> "
            f"(targeting '{INTEGRATION_BRANCH}')."
        )
    suffix = branch[len("dev-") :]
    if suffix.lower() != author_login.lower():
        # Otherwise one contributor could open work under another's name.
        raise ReviewError(
            f"Branch '{branch}' must end with the author login; expected "
            f"'dev-{author_login}'."
        )


WRITE_PERMISSIONS = frozenset({"write", "maintain", "admin"})


def evaluate_authorization(
    client: GitHubClient,
    repository: str,
    author_login: str,
    author_bot_id: int,
    allowed_bots: dict[str, int],
) -> str:
    """Confirm the author is a write-access collaborator or an allow-listed bot."""

    expected_bot_id = allowed_bots.get(author_login.lower())
    if expected_bot_id is not None:
        if author_bot_id != expected_bot_id:
            raise ReviewError(
                f"Author '{author_login}' does not match its allow-listed user id."
            )
        return f"allow-listed bot {author_login}"

    # Mirrors the collaborator permission lookup auto_merge.py uses for approvals.
    encoded_login = urllib.parse.quote(author_login, safe="")
    try:
        response = client.get_json(
            f"repos/{repository}/collaborators/{encoded_login}/permission"
        )
    except Exception as exc:  # noqa: BLE001 - any lookup failure must fail closed
        raise ReviewError(
            f"Unable to resolve collaborator permission for '{author_login}'."
        ) from exc
    mapping = require_mapping(response, "Collaborator permission")
    permission = str(mapping.get("permission") or "")
    if permission not in WRITE_PERMISSIONS:
        reported = permission or "none"
        raise ReviewError(
            f"Author '{author_login}' is not a repository collaborator with write "
            f"access (permission: '{reported}')."
        )
    return f"collaborator {author_login} ({permission})"


def resolve_pull_request(event: dict[str, Any], repository_id: int) -> dict[str, Any]:
    """Extract and bind the pull request, or report that the gate does not apply."""

    pull_request = event.get("pull_request")
    if not isinstance(pull_request, dict):
        raise ReviewError("Contributor gate requires a pull request event.")
    base = require_mapping(pull_request.get("base"), "Pull request base")
    head = require_mapping(pull_request.get("head"), "Pull request head")
    head_sha = str(head.get("sha") or "")
    if not SHA_RE.fullmatch(head_sha):
        raise ReviewError("Pull request head SHA is invalid.")
    # A release-branch pull request is the promotion gate's responsibility.
    if str(base.get("ref") or "") != INTEGRATION_BRANCH:
        return {"applies": False}
    head_repository = require_mapping(head.get("repo"), "Pull request head repository")
    if head_repository.get("id") != repository_id:
        raise ReviewError(
            "Contributor pull requests must originate from a branch in this "
            "repository, not a fork."
        )
    return {
        "applies": True,
        "branch": str(head.get("ref") or ""),
        "head_sha": head_sha,
        "number": pull_request.get("number"),
        "pull_request": pull_request,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--repository-id", required=True)
    parser.add_argument("--event-path", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--allowed-bots", default="")
    args = parser.parse_args(argv)

    repository = require_repository(args.repository)
    if not str(args.repository_id).isdigit():
        raise ReviewError("Repository id is invalid.")
    repository_id = int(args.repository_id)
    event = read_json(Path(args.event_path))
    if not isinstance(event, dict):
        raise ReviewError("Event payload must be a JSON object.")
    allowed_bots = allowed_bot_identities(args.allowed_bots)

    binding = resolve_pull_request(event, repository_id)
    if not binding["applies"]:
        print("Contributor gate does not apply to this base branch; skipping.")
        return 0

    branch = str(binding["branch"])
    head_sha = str(binding["head_sha"])
    client = GitHubClient(os.environ.get("GH_TOKEN", ""))
    try:
        author_login, _author_type, author_bot_id = author_identity(
            binding["pull_request"]
        )
        evaluate_branch_naming(branch, author_login, author_bot_id, allowed_bots)
        authorization = evaluate_authorization(
            client, repository, author_login, author_bot_id, allowed_bots
        )
    except ReviewError as exc:
        publish_status(
            client,
            repository,
            head_sha,
            "failure",
            str(exc),
            args.run_url,
            context=CONTRIBUTOR_STATUS_CONTEXT,
        )
        print(f"::error::{exc}")
        return 1

    publish_status(
        client,
        repository,
        head_sha,
        "success",
        f"{branch} accepted for {authorization}.",
        args.run_url,
        context=CONTRIBUTOR_STATUS_CONTEXT,
    )
    print(f"Contributor gate passed: {branch} / {authorization}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except ReviewError as error:
        print(f"::error::{error}")
        sys.exit(1)
