import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).with_name("agent_harness.py").resolve()


class AgentHarnessTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.run_command("git", "init", "-q")
        self.run_command("git", "config", "user.email", "harness@example.test")
        self.run_command("git", "config", "user.name", "Harness Test")
        (self.root / ".gitignore").write_text(".agent-harness/\n", encoding="utf-8")
        (self.root / "tracked.txt").write_text("initial\n", encoding="utf-8")
        self.write_config(
            test_commands=[
                {
                    "name": "test-ok",
                    "argv": [sys.executable, "-c", "print('test ok')"],
                }
            ],
            verify_commands=[
                {
                    "name": "verify-ok",
                    "argv": [sys.executable, "-c", "print('verify ok')"],
                }
            ],
        )
        self.commit_all("initial")

    def tearDown(self):
        self.temporary_directory.cleanup()

    def run_command(self, *argv):
        return subprocess.run(
            argv,
            cwd=str(self.root),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=True,
        )

    def commit_all(self, message):
        self.run_command("git", "add", ".")
        self.run_command("git", "commit", "-q", "-m", message)

    def write_config(self, test_commands, verify_commands):
        config = {
            "schema_version": 1,
            "commands": {
                "test": test_commands,
                "verify": verify_commands,
            },
            "manual_check_guidance": [],
        }
        (self.root / "harness.config.json").write_text(
            json.dumps(config),
            encoding="utf-8",
        )

    def harness(self, *args, payload=None):
        return subprocess.run(
            [sys.executable, str(SCRIPT), *args],
            cwd=str(self.root),
            input=json.dumps(payload) if payload is not None else None,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )

    def begin_and_plan(
        self,
        manual_check=None,
        adopt_existing=False,
        harness_maintenance=False,
    ):
        begin_arguments = ["begin", "--request", "테스트 작업"]
        if adopt_existing:
            begin_arguments.append("--adopt-existing")
        if harness_maintenance:
            begin_arguments.append("--harness-maintenance")
        begin = self.harness(*begin_arguments)
        self.assertEqual(0, begin.returncode, begin.stderr)
        arguments = [
            "plan",
            "--assumption",
            "테스트 저장소를 사용한다.",
            "--acceptance",
            "게이트가 통과한다.",
            "--step",
            "테스트를 실행한다.",
        ]
        if manual_check:
            arguments.extend(["--manual-check", manual_check])
        plan = self.harness(*arguments)
        self.assertEqual(0, plan.returncode, plan.stderr)

    def hook(self, event, **extra):
        payload = {
            "session_id": "session-test",
            "turn_id": "turn-test",
            "cwd": str(self.root),
            "hook_event_name": event,
            "permission_mode": "default",
        }
        payload.update(extra)
        result = self.harness("hook", payload=payload)
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads(result.stdout)

    def read_state(self):
        return json.loads(
            (self.root / ".agent-harness" / "current.json").read_text(
                encoding="utf-8"
            )
        )

    def status_json(self):
        result = self.harness("status", "--json")
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads(result.stdout)

    def test_happy_path_and_changed_file_make_result_stale(self):
        self.begin_and_plan()

        test_result = self.harness("run", "test")
        self.assertEqual(0, test_result.returncode, test_result.stderr)
        verify_result = self.harness("run", "verify")
        self.assertEqual(0, verify_result.returncode, verify_result.stderr)
        gate = self.harness("gate")
        self.assertEqual(0, gate.returncode, gate.stdout + gate.stderr)

        (self.root / "tracked.txt").write_text("changed\n", encoding="utf-8")
        stale_gate = self.harness("gate")
        self.assertNotEqual(0, stale_gate.returncode)
        stop_output = self.hook(
            "Stop",
            stop_hook_active=False,
            last_assistant_message="완료",
        )
        self.assertEqual("block", stop_output.get("decision"))
        self.assertIn("stale", stop_output.get("reason", ""))

    def test_failure_requires_plan_revision_before_retry(self):
        self.write_config(
            test_commands=[
                {
                    "name": "conditional-test",
                    "argv": [
                        sys.executable,
                        "-c",
                        (
                            "from pathlib import Path; "
                            "raise SystemExit(0 if Path('allow-test').exists() else 9)"
                        ),
                    ],
                }
            ],
            verify_commands=[
                {
                    "name": "verify-ok",
                    "argv": [sys.executable, "-c", "print('verify ok')"],
                }
            ],
        )
        self.commit_all("conditional test")
        self.begin_and_plan()

        failed = self.harness("run", "test")
        self.assertEqual(1, failed.returncode)
        retry_without_revision = self.harness("run", "test")
        self.assertEqual(2, retry_without_revision.returncode)

        blocked = self.harness("block", "--reason", "사용자 확인이 필요하다.")
        self.assertEqual(0, blocked.returncode, blocked.stderr)
        self.assertEqual("blocked", self.status_json()["status"])
        self.assertEqual(
            {},
            self.hook(
                "Stop",
                stop_hook_active=False,
                last_assistant_message="질문",
            ),
        )
        revised = self.harness(
            "revise",
            "--cause",
            "필수 파일이 없었다.",
            "--change",
            "필수 파일을 만들고 다시 테스트한다.",
        )
        self.assertEqual(0, revised.returncode, revised.stderr)
        (self.root / "allow-test").write_text("yes\n", encoding="utf-8")
        self.assertEqual(0, self.harness("run", "test").returncode)
        self.assertEqual(0, self.harness("run", "verify").returncode)
        self.assertEqual(0, self.harness("gate").returncode)

        state = self.read_state()
        self.assertEqual(2, state["plan_version"])
        self.assertIsNotNone(state["failures"][0]["resolved_by_plan_version"])

    def test_manual_check_needs_fresh_evidence(self):
        self.begin_and_plan(manual_check="화면을 직접 확인한다.")
        self.assertEqual(0, self.harness("run", "test").returncode)

        missing_evidence = self.harness("run", "verify")
        self.assertEqual(2, missing_evidence.returncode)
        evidence = self.harness(
            "evidence",
            "--check",
            "M1",
            "--result",
            "테스트 화면에서 정상 동작을 확인했다.",
        )
        self.assertEqual(0, evidence.returncode, evidence.stderr)
        self.assertEqual(0, self.harness("run", "verify").returncode)
        self.assertEqual(0, self.harness("gate").returncode)

    def test_hooks_allow_read_only_but_block_unplanned_edits_and_stop(self):
        edit_without_task = self.hook(
            "PreToolUse",
            tool_name="apply_patch",
            tool_use_id="tool-test",
            tool_input={"command": "patch"},
        )
        decision = edit_without_task["hookSpecificOutput"]["permissionDecision"]
        self.assertEqual("deny", decision)
        self.assertFalse((self.root / ".agent-harness").exists())
        self.assertEqual(0, self.harness("status").returncode)
        self.assertFalse((self.root / ".agent-harness").exists())
        self.assertEqual(
            {},
            self.hook(
                "Stop",
                stop_hook_active=False,
                last_assistant_message="읽기 전용 답변",
            ),
        )
        self.begin_and_plan()
        self.assertEqual(
            {},
            self.hook(
                "PreToolUse",
                tool_name="apply_patch",
                tool_use_id="tool-test",
                tool_input={"command": "patch"},
            ),
        )
        stop_in_progress = self.hook(
            "Stop",
            stop_hook_active=False,
            last_assistant_message="완료",
        )
        self.assertEqual("block", stop_in_progress.get("decision"))

    def test_block_from_planning_allows_stop_and_can_resume_planning(self):
        begin = self.harness("begin", "--request", "결정이 필요한 작업")
        self.assertEqual(0, begin.returncode, begin.stderr)
        blocked = self.harness("block", "--reason", "요구사항 확인이 필요하다.")
        self.assertEqual(0, blocked.returncode, blocked.stderr)
        self.assertEqual("blocked", self.status_json()["status"])
        self.assertEqual(
            {},
            self.hook(
                "Stop",
                stop_hook_active=False,
                last_assistant_message="질문",
            ),
        )
        revised = self.harness(
            "revise",
            "--cause",
            "사용자가 요구사항을 결정했다.",
            "--change",
            "확정된 요구사항으로 계획한다.",
        )
        self.assertEqual(0, revised.returncode, revised.stderr)
        self.assertEqual("planning", self.read_state()["phase"])
        plan = self.harness(
            "plan",
            "--acceptance",
            "확정된 요구사항을 만족한다.",
            "--step",
            "구현한다.",
        )
        self.assertEqual(0, plan.returncode, plan.stderr)

    def test_running_stage_rejects_concurrent_block_and_revision(self):
        self.write_config(
            test_commands=[
                {
                    "name": "slow-test",
                    "argv": [
                        sys.executable,
                        "-c",
                        "import time; time.sleep(0.5); print('done')",
                    ],
                }
            ],
            verify_commands=[
                {
                    "name": "verify-ok",
                    "argv": [sys.executable, "-c", "print('verify ok')"],
                }
            ],
        )
        self.commit_all("slow test")
        self.begin_and_plan()
        process = subprocess.Popen(
            [sys.executable, str(SCRIPT), "run", "test"],
            cwd=str(self.root),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        deadline = time.time() + 3
        while time.time() < deadline:
            state_path = self.root / ".agent-harness" / "current.json"
            if state_path.exists() and self.read_state().get("phase") == "testing":
                break
            time.sleep(0.02)
        else:
            self.fail("test stage did not enter testing phase")

        block = self.harness("block", "--reason", "동시 차단")
        revise = self.harness(
            "revise",
            "--cause",
            "동시 수정",
            "--change",
            "바뀌면 안 된다.",
        )
        self.assertEqual(2, block.returncode)
        self.assertEqual(2, revise.returncode)
        stdout, stderr = process.communicate(timeout=5)
        self.assertEqual(0, process.returncode, stdout + stderr)
        self.assertEqual("tested", self.read_state()["phase"])

    def test_dirty_workspace_requires_explicit_adoption(self):
        (self.root / "tracked.txt").write_text("existing change\n", encoding="utf-8")
        denied = self.harness("begin", "--request", "기존 변경 작업")
        self.assertEqual(2, denied.returncode)
        adopted = self.harness(
            "begin",
            "--request",
            "기존 변경 작업",
            "--adopt-existing",
        )
        self.assertEqual(0, adopted.returncode, adopted.stderr)
        self.assertTrue(self.read_state()["adopted_existing"])

    def test_staging_and_commit_of_verified_content_keep_gate_fresh(self):
        self.begin_and_plan()
        (self.root / "tracked.txt").write_text("verified change\n", encoding="utf-8")
        self.assertEqual(0, self.harness("run", "test").returncode)
        self.assertEqual(0, self.harness("run", "verify").returncode)
        self.run_command("git", "add", "tracked.txt")
        self.assertEqual(0, self.harness("gate").returncode)
        self.run_command("git", "commit", "-q", "-m", "verified change")
        self.assertEqual(0, self.harness("gate").returncode)

    def test_staged_content_must_match_tested_worktree(self):
        (self.root / "tracked.txt").write_text("staged version\n", encoding="utf-8")
        self.run_command("git", "add", "tracked.txt")
        (self.root / "tracked.txt").write_text("worktree version\n", encoding="utf-8")
        self.begin_and_plan(adopt_existing=True)
        result = self.harness("run", "test")
        self.assertEqual(2, result.returncode)
        self.assertIn("staged", result.stderr)

    def test_bash_hook_allows_reads_but_blocks_unplanned_writes(self):
        safe = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="safe",
            tool_input={"command": "git status --short"},
        )
        safe_control_read = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="safe-control-read",
            tool_input={"command": "cat AGENTS.md"},
        )
        unsafe = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="unsafe",
            tool_input={
                "command": "python3 -c \"from pathlib import Path; Path('x').write_text('x')\""
            },
        )
        harness_command = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="harness",
            tool_input={"command": "./scripts/harness begin --request task"},
        )
        quoted_harness_command = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="quoted-harness",
            tool_input={
                "command": "./scripts/harness begin --request 'foo; bar'"
            },
        )
        safe_quoted_rg = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="safe-quoted-rg",
            tool_input={"command": "rg 'foo|bar' README.md"},
        )
        chained_harness_command = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="chained-harness",
            tool_input={
                "command": "./scripts/harness status; touch SHOULD_NOT_EXIST"
            },
        )
        background_harness_command = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="background-harness",
            tool_input={
                "command": "./scripts/harness status & touch SHOULD_NOT_EXIST"
            },
        )
        foreign_harness_command = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="foreign-harness",
            tool_input={"command": "/private/tmp/evil/scripts/harness status"},
        )
        foreign_read_command = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="foreign-read",
            tool_input={"command": "/private/tmp/evil/cat README.md"},
        )
        subdirectory = self.root / "nested"
        subdirectory.mkdir()
        subdirectory_harness_command = self.hook(
            "PreToolUse",
            cwd=str(subdirectory),
            tool_name="Bash",
            tool_use_id="subdirectory-harness",
            tool_input={"command": "./scripts/harness status"},
        )
        unsafe_rg = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="unsafe-rg",
            tool_input={"command": "rg --pre /bin/rm never-match victim.txt"},
        )
        unsafe_git_grep = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="unsafe-git-grep",
            tool_input={
                "command": "git grep --open-files-in-pager=/bin/rm hello"
            },
        )
        unsafe_abbreviated_git_grep = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="unsafe-abbreviated-git-grep",
            tool_input={"command": "git grep --open-files=/bin/rm hello"},
        )
        unsafe_abbreviated_git_diff = self.hook(
            "PreToolUse",
            tool_name="Bash",
            tool_use_id="unsafe-abbreviated-git-diff",
            tool_input={"command": "git diff --ext"},
        )
        self.assertEqual({}, safe)
        self.assertEqual({}, safe_control_read)
        self.assertEqual("deny", unsafe["hookSpecificOutput"]["permissionDecision"])
        self.assertEqual({}, harness_command)
        self.assertEqual({}, quoted_harness_command)
        self.assertEqual({}, safe_quoted_rg)
        for output in (
            chained_harness_command,
            background_harness_command,
            foreign_harness_command,
            foreign_read_command,
            subdirectory_harness_command,
            unsafe_rg,
            unsafe_git_grep,
            unsafe_abbreviated_git_grep,
            unsafe_abbreviated_git_diff,
        ):
            self.assertEqual(
                "deny",
                output["hookSpecificOutput"]["permissionDecision"],
            )

    def test_control_path_globs_are_denied_outside_maintenance(self):
        self.begin_and_plan()
        for index, command in enumerate(
            (
                "sed -i.bak 's/return 1/return 0/' tools/*.py",
                "rm -rf .agent-*",
                "rm -rf .codex",
                "rm tools/{agent_harness.py,test_agent_harness.py}",
                "rm -rf .agent-$(printf harness)",
            )
        ):
            output = self.hook(
                "PreToolUse",
                tool_name="Bash",
                tool_use_id="control-glob-{}".format(index),
                tool_input={"command": command},
            )
            self.assertEqual(
                "deny",
                output["hookSpecificOutput"]["permissionDecision"],
            )

    def test_harness_control_files_require_maintenance_task(self):
        self.begin_and_plan()
        denied = self.hook(
            "PreToolUse",
            tool_name="apply_patch",
            tool_use_id="control",
            tool_input={"command": "*** Update File: tools/agent_harness.py"},
        )
        self.assertEqual("deny", denied["hookSpecificOutput"]["permissionDecision"])

    def test_harness_maintenance_task_can_edit_control_files(self):
        self.begin_and_plan(harness_maintenance=True)
        allowed = self.hook(
            "PreToolUse",
            tool_name="apply_patch",
            tool_use_id="control",
            tool_input={"command": "*** Update File: tools/agent_harness.py"},
        )
        self.assertEqual({}, allowed)

    def test_plan_must_precede_workspace_changes(self):
        begin = self.harness("begin", "--request", "순서 검사")
        self.assertEqual(0, begin.returncode, begin.stderr)
        (self.root / "tracked.txt").write_text("too early\n", encoding="utf-8")
        plan = self.harness(
            "plan",
            "--acceptance",
            "통과한다.",
            "--step",
            "구현한다.",
        )
        self.assertEqual(2, plan.returncode)
        self.assertIn("계획 전에", plan.stderr)

    def test_empty_required_command_list_fails_closed(self):
        self.write_config(
            test_commands=[],
            verify_commands=[
                {
                    "name": "verify-ok",
                    "argv": [sys.executable, "-c", "print('verify ok')"],
                }
            ],
        )
        self.commit_all("empty test commands")
        result = self.harness("begin", "--request", "실패해야 하는 작업")
        self.assertEqual(2, result.returncode)
        self.assertIn("하나 이상", result.stderr)

    def test_required_plan_and_reason_text_must_not_be_blank(self):
        begin = self.harness("begin", "--request", "빈 값 검사")
        self.assertEqual(0, begin.returncode, begin.stderr)
        blank_acceptance = self.harness(
            "plan",
            "--acceptance",
            "   ",
            "--step",
            "구현한다.",
        )
        blank_step = self.harness(
            "plan",
            "--acceptance",
            "통과한다.",
            "--step",
            "   ",
        )
        self.assertEqual(2, blank_acceptance.returncode)
        self.assertEqual(2, blank_step.returncode)

        plan = self.harness(
            "plan",
            "--acceptance",
            "통과한다.",
            "--step",
            "구현한다.",
            "--manual-check",
            "화면을 확인한다.",
        )
        self.assertEqual(0, plan.returncode, plan.stderr)
        self.assertEqual(
            2,
            self.harness(
                "revise",
                "--cause",
                "   ",
                "--change",
                "계획을 바꾼다.",
            ).returncode,
        )
        self.assertEqual(
            2,
            self.harness(
                "evidence",
                "--check",
                "M1",
                "--result",
                "   ",
            ).returncode,
        )
        self.assertEqual(
            2,
            self.harness("block", "--reason", "   ").returncode,
        )


if __name__ == "__main__":
    unittest.main()
