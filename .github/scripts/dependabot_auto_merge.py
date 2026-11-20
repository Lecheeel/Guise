"""Enable GitHub auto-merge for eligible, verified Dependabot updates only."""

import json
import os
import re
import subprocess
from pathlib import Path


SMALL_UPDATES = {"version-update:semver-patch", "version-update:semver-minor"}
GRADLE_FILES = {
    "gradle/libs.versions.toml", "build.gradle.kts",
    "app/build.gradle.kts", "hook/build.gradle.kts",
}
TOOLCHAIN = {
    "gradle", "gradle-wrapper", "com.android.application", "com.android.library",
}
TOOLCHAIN_PREFIXES = (
    "com.android.tools.build:", "org.jetbrains.kotlin:",
    "org.jetbrains.kotlin.", "com.google.devtools.ksp",
)


def eligible(dependencies, files):
    """Every member of a grouped update must pass. Unknown updates stay manual."""
    if not isinstance(dependencies, list) or not dependencies or not files:
        return False, "Missing dependency metadata or changed files"
    ecosystems = set()
    for dependency in dependencies:
        if not isinstance(dependency, dict):
            return False, "Invalid dependency metadata"
        name = dependency.get("dependencyName", "")
        ecosystem = dependency.get("packageEcosystem", "")
        if not isinstance(name, str) or not name:
            return False, "Missing dependency name"
        if ecosystem not in {"gradle", "github_actions", "github-actions"}:
            return False, "Unsupported ecosystem"
        ecosystems.add("actions" if ecosystem != "gradle" else "gradle")
        if dependency.get("updateType") not in SMALL_UPDATES:
            return False, "Major or unclassified update requires review"
        if dependency.get("maintainerChanges"):
            return False, "Dependency maintainer changes require review"
        if name in TOOLCHAIN or name.startswith(TOOLCHAIN_PREFIXES):
            return False, "Android/Kotlin/Gradle toolchain requires review"
        if dependency.get("dependencyGroup") == "android-toolchain":
            return False, "Toolchain group requires review"
        if ecosystem == "gradle":
            version = dependency.get("newVersion", "")
            if not isinstance(version, str) or not re.fullmatch(r"\d+(?:\.\d+)+", version):
                return False, "Unknown or prerelease Gradle version requires review"
            if name.startswith("io.github.libxposed:"):
                previous = dependency.get("prevVersion", "")
                if not isinstance(previous, str) or not previous.startswith("102.") or not version.startswith("102."):
                    return False, "libxposed API migration requires review"
    if len(ecosystems) != 1:
        return False, "Mixed ecosystems require review"
    for file in files:
        path = file.get("filename", "")
        if file.get("status") != "modified":
            return False, "Added, deleted, or renamed files require review"
        if ecosystems == {"gradle"}:
            if path not in GRADLE_FILES:
                return False, "Changes outside dependency manifests require review"
        elif not (path.startswith(".github/workflows/") and path.endswith((".yml", ".yaml"))):
            return False, "Changes outside Actions workflows require review"
    return True, "All dependencies are eligible patch/minor updates"


def gh(*arguments):
    return subprocess.check_output(["gh", *arguments], text=True)


def api(path):
    return json.loads(gh("api", path))


def paginated(path):
    return [item for page in json.loads(gh("api", "--paginate", "--slurp", path)) for item in page]


def main():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    repo = os.environ["GITHUB_REPOSITORY"]
    number = event["pull_request"]["number"]
    expected_head = event["pull_request"]["head"]["sha"]
    pull = api(f"repos/{repo}/pulls/{number}")
    if pull["head"]["sha"] != expected_head or pull["state"] != "open":
        print("Stale event or closed PR; no change")
        return
    allowed = (
        pull["user"]["login"] == "dependabot[bot]"
        and pull["head"]["repo"]["full_name"] == repo
        and pull["base"]["ref"] == "main"
        and not pull["draft"]
    )
    reason = "Only non-draft Dependabot PRs from this repository are eligible"
    if allowed:
        commits = paginated(f"repos/{repo}/pulls/{number}/commits")
        allowed = bool(commits) and all(
            (commit.get("author") or {}).get("login") == "dependabot[bot]"
            and commit["commit"].get("verification", {}).get("verified") is True
            for commit in commits
        )
        reason = "Every commit must be signed and authored by Dependabot"
    if allowed:
        allowed, reason = eligible(
            json.loads(os.environ.get("UPDATED_DEPENDENCIES") or "[]"),
            paginated(f"repos/{repo}/pulls/{number}/files"),
        )
    print(reason)
    if not allowed:
        if pull.get("auto_merge"):
            gh("pr", "merge", str(number), "--repo", repo, "--disable-auto")
            print("Removed existing auto-merge request; manual review required")
        return
    # Do not enable auto-merge without a real, strict build gate.
    repository = api(f"repos/{repo}")
    protection = api(f"repos/{repo}/branches/main/protection")
    checks = protection.get("required_status_checks") or {}
    if not repository.get("allow_auto_merge") or not checks.get("strict") or not any(
        check.get("context") == "build" and check.get("app_id") == 15368
        for check in checks.get("checks", [])
    ):
        raise RuntimeError("Auto-merge requires the protected GitHub Actions build check")
    gh("pr", "merge", str(number), "--repo", repo, "--auto", "--squash",
       "--match-head-commit", expected_head)
    print("Auto-merge requested; GitHub enforces the required build check")


if __name__ == "__main__":
    main()
