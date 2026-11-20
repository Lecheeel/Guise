import copy
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import dependabot_auto_merge as policy


def dependency(**overrides):
    return {
        "dependencyName": "io.insert-koin:koin-bom",
        "packageEcosystem": "gradle",
        "updateType": "version-update:semver-minor",
        "prevVersion": "4.2.2",
        "newVersion": "4.3.0",
        **overrides,
    }


FILES = [{"filename": "gradle/libs.versions.toml", "status": "modified"}]


class PolicyTests(unittest.TestCase):
    def test_all_group_members_must_pass(self):
        self.assertTrue(policy.eligible([dependency()], FILES)[0])
        for bad in [
            dependency(updateType="version-update:semver-major"),
            dependency(updateType=""),
            dependency(newVersion="4.3.0-beta01"),
            dependency(newVersion="latest.release"),
            dependency(maintainerChanges=True),
            dependency(packageEcosystem="npm"),
            dependency(dependencyGroup="android-toolchain"),
            {},
            None,
        ]:
            with self.subTest(dependency=bad):
                self.assertFalse(policy.eligible([dependency(), bad], FILES)[0])

    def test_toolchain_always_requires_review(self):
        for name in [
            "gradle", "gradle-wrapper", "com.android.application", "com.android.library",
            "com.android.tools.build:gradle", "org.jetbrains.kotlin:kotlin-gradle-plugin",
            "org.jetbrains.kotlin.plugin.compose", "org.jetbrains.kotlin.android",
            "com.google.devtools.ksp:symbol-processing-gradle-plugin", "com.google.devtools.ksp",
        ]:
            with self.subTest(name=name):
                self.assertFalse(policy.eligible([dependency(dependencyName=name)], FILES)[0])
        self.assertTrue(policy.eligible([
            dependency(dependencyName="org.jetbrains.kotlinx:kotlinx-coroutines-android")
        ], FILES)[0])

    def test_libxposed_stays_in_api_102(self):
        for name in ["io.github.libxposed:api", "io.github.libxposed:service"]:
            self.assertTrue(policy.eligible([
                dependency(dependencyName=name, prevVersion="102.0.0", newVersion="102.0.1")
            ], FILES)[0])
            for previous, new in [("102.0.0", "103.0.0"), ("101.0.0", "102.0.0"), ("", "102.0.1")]:
                self.assertFalse(policy.eligible([
                    dependency(dependencyName=name, prevVersion=previous, newVersion=new)
                ], FILES)[0])

    def test_only_expected_files_can_change(self):
        for files in [[], [{"filename": "script/verify_module_apk.py", "status": "modified"}],
                      [{"filename": "gradle/wrapper/gradle-wrapper.properties", "status": "modified"}],
                      [{"filename": "gradle/libs.versions.toml", "status": "added"}],
                      [{"filename": "gradle/libs.versions.toml", "status": "renamed"}]]:
            self.assertFalse(policy.eligible([dependency()], files)[0])
        self.assertFalse(policy.eligible([], FILES)[0])
        self.assertFalse(policy.eligible({}, FILES)[0])

    def test_action_sha_updates_and_mixed_groups(self):
        action = dependency(dependencyName="actions/checkout", packageEcosystem="github_actions",
                            newVersion="a" * 40)
        files = [{"filename": ".github/workflows/android.yml", "status": "modified"}]
        self.assertTrue(policy.eligible([action], files)[0])
        self.assertFalse(policy.eligible([action, dependency()], files)[0])
        self.assertFalse(policy.eligible([action], FILES)[0])
        self.assertFalse(policy.eligible([
            action, {**action, "updateType": "version-update:semver-major"}
        ], files)[0])


class AutomationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        event = Path(self.directory.name) / "event.json"
        event.write_text(json.dumps({"pull_request": {"number": 1, "head": {"sha": "a" * 40}}}))
        environment = patch.dict(os.environ, {
            "GITHUB_EVENT_PATH": str(event), "GITHUB_REPOSITORY": "owner/repo",
            "UPDATED_DEPENDENCIES": json.dumps([dependency()]),
        })
        environment.start()
        self.addCleanup(environment.stop)
        self.pull = {
            "head": {"sha": "a" * 40, "repo": {"full_name": "owner/repo"}},
            "base": {"ref": "main"}, "state": "open", "draft": False,
            "user": {"login": "dependabot[bot]"}, "auto_merge": None,
        }
        self.commits = [{"author": {"login": "dependabot[bot]"},
                         "commit": {"verification": {"verified": True}}}]
        self.protection = {"required_status_checks": {"strict": True, "checks": [
            {"context": "build", "app_id": 15368}
        ]}}

    def run_policy(self, protection=None, auto_merge=True):
        def api(path):
            if path.endswith("/pulls/1"):
                return self.pull
            if path.endswith("/protection"):
                return self.protection if protection is None else protection
            return {"allow_auto_merge": auto_merge}

        def paginated(path):
            return self.commits if path.endswith("/commits") else FILES

        with patch.object(policy, "api", side_effect=api), \
             patch.object(policy, "paginated", side_effect=paginated), \
             patch.object(policy, "gh") as gh:
            policy.main()
            return gh.call_args_list

    def test_enables_github_auto_merge_for_exact_head(self):
        calls = self.run_policy()
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0].args, (
            "pr", "merge", "1", "--repo", "owner/repo", "--auto", "--squash",
            "--match-head-commit", "a" * 40,
        ))

    def test_refuses_missing_or_untrusted_build_gate(self):
        for protection in [{}, {"required_status_checks": {"strict": False, "checks": []}},
                           {"required_status_checks": {"strict": True, "checks": [
                               {"context": "build", "app_id": -1}
                           ]}}]:
            with self.subTest(protection=protection), self.assertRaises(RuntimeError):
                self.run_policy(protection=protection)
        with self.assertRaises(RuntimeError):
            self.run_policy(auto_merge=False)

    def test_stale_event_does_not_change_pr(self):
        self.pull["head"]["sha"] = "b" * 40
        self.assertEqual(self.run_policy(), [])

    def test_unverified_or_human_commit_does_not_merge(self):
        for commit in [{"author": None, "commit": {"verification": {"verified": True}}},
                       {"author": {"login": "dependabot[bot]"}, "commit": {"verification": {"verified": False}}}]:
            with self.subTest(commit=commit):
                self.commits.append(commit)
                self.assertEqual(self.run_policy(), [])
                self.commits.pop()

    def test_major_update_revokes_existing_auto_merge(self):
        self.pull["auto_merge"] = {"enabled_by": {"login": "github-actions[bot]"}}
        with patch.dict(os.environ, {"UPDATED_DEPENDENCIES": json.dumps([
            dependency(updateType="version-update:semver-major")
        ])}):
            calls = self.run_policy()
        self.assertEqual(calls[0].args, ("pr", "merge", "1", "--repo", "owner/repo", "--disable-auto"))

    def test_human_fork_and_draft_prs_stay_manual(self):
        original = copy.deepcopy(self.pull)
        for field in ["author", "fork", "draft"]:
            self.pull = copy.deepcopy(original)
            if field == "author": self.pull["user"]["login"] = "maintainer"
            if field == "fork": self.pull["head"]["repo"]["full_name"] = "someone/fork"
            if field == "draft": self.pull["draft"] = True
            self.assertEqual(self.run_policy(), [])


if __name__ == "__main__":
    unittest.main()
