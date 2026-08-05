#!/usr/bin/env python3
"""Stateful plan-test-verify gate for Codex work in this repository."""

import argparse
import datetime as dt
import fcntl
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import uuid
from contextlib import contextmanager


SCHEMA_VERSION = 1
CONFIG_FILE = "harness.config.json"
STATE_DIRECTORY = ".agent-harness"
CURRENT_STATE_FILE = "current.json"
EVENTS_FILE = "events.jsonl"
HARNESS_CONTROL_PATHS = (
    STATE_DIRECTORY,
    ".codex/hooks.json",
    "AGENTS.md",
    "harness.config.json",
    "scripts/harness",
    "tools/agent_harness.py",
    "tools/test_agent_harness.py",
)


class HarnessError(RuntimeError):
    pass


def utc_now():
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()


def run_git(root, *args, check=True, binary=False):
    result = subprocess.run(
        ["git", *args],
        cwd=str(root),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=not binary,
    )
    if check and result.returncode != 0:
        stderr = result.stderr if not binary else result.stderr.decode("utf-8", "replace")
        raise HarnessError("git 명령 실패: " + stderr.strip())
    return result


def find_repo_root(cwd=None):
    start = Path(cwd or os.getcwd())
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        cwd=str(start),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if result.returncode != 0:
        raise HarnessError("Git 저장소 루트에서 실행해야 합니다.")
    return Path(result.stdout.strip()).resolve()


def state_directory(root):
    return root / STATE_DIRECTORY


def state_path(root):
    return state_directory(root) / CURRENT_STATE_FILE


@contextmanager
def state_lock(root):
    directory = state_directory(root)
    directory.mkdir(parents=True, exist_ok=True)
    lock_path = directory / ".lock"
    with lock_path.open("a+", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


@contextmanager
def state_read_lock(root):
    lock_path = state_directory(root) / ".lock"
    if not lock_path.exists():
        yield
        return
    try:
        with lock_path.open("r", encoding="utf-8") as lock_file:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_SH)
            try:
                yield
            finally:
                fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)
    except FileNotFoundError:
        yield


def read_json(path):
    try:
        with path.open("r", encoding="utf-8") as file:
            return json.load(file)
    except (OSError, json.JSONDecodeError) as exc:
        raise HarnessError("{} 파일을 읽을 수 없습니다: {}".format(path, exc))


def write_json_atomic(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=path.name + ".",
        suffix=".tmp",
        dir=str(path.parent),
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as file:
            json.dump(value, file, ensure_ascii=False, indent=2)
            file.write("\n")
            file.flush()
            os.fsync(file.fileno())
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def load_state(root):
    path = state_path(root)
    if not path.exists():
        return None
    state = read_json(path)
    if state.get("schema_version") != SCHEMA_VERSION:
        raise HarnessError("지원하지 않는 하네스 상태 버전입니다.")
    return state


def save_state(root, state):
    state["updated_at"] = utc_now()
    write_json_atomic(state_path(root), state)


def append_event(root, event_type, state=None, **details):
    event = {
        "at": utc_now(),
        "event": event_type,
        "task_id": state.get("task_id") if state else None,
        "phase": state.get("phase") if state else None,
    }
    event.update(details)
    path = state_directory(root) / EVENTS_FILE
    with path.open("a", encoding="utf-8") as file:
        file.write(json.dumps(event, ensure_ascii=False) + "\n")
        file.flush()
        os.fsync(file.fileno())


def load_config(root):
    path = root / CONFIG_FILE
    if not path.exists():
        raise HarnessError("{} 파일이 없습니다. 검증을 통과시킬 수 없습니다.".format(CONFIG_FILE))
    config = read_json(path)
    if config.get("schema_version") != SCHEMA_VERSION:
        raise HarnessError("지원하지 않는 하네스 설정 버전입니다.")
    commands = config.get("commands")
    if not isinstance(commands, dict):
        raise HarnessError("하네스 명령 설정이 없습니다.")
    for stage in ("test", "verify"):
        entries = commands.get(stage)
        if not isinstance(entries, list) or not entries:
            raise HarnessError("{} 명령은 하나 이상이어야 합니다.".format(stage))
        for entry in entries:
            if not isinstance(entry, dict) or not isinstance(entry.get("name"), str):
                raise HarnessError("{} 명령 이름이 올바르지 않습니다.".format(stage))
            argv = entry.get("argv")
            if (
                not isinstance(argv, list)
                or not argv
                or not all(isinstance(item, str) and item for item in argv)
            ):
                raise HarnessError("{} 명령 argv가 올바르지 않습니다.".format(stage))
    return config


def workspace_fingerprint(root):
    listed = run_git(
        root,
        "ls-files",
        "-z",
        "--cached",
        "--others",
        "--exclude-standard",
        binary=True,
    ).stdout

    digest = hashlib.sha256()
    for raw_path in sorted(item for item in listed.split(b"\0") if item):
        relative = os.fsdecode(raw_path)
        if relative == STATE_DIRECTORY or relative.startswith(STATE_DIRECTORY + "/"):
            continue
        path = root / relative
        try:
            stat_result = path.lstat()
        except FileNotFoundError:
            continue
        digest.update(b"\0PATH\0")
        digest.update(raw_path)
        digest.update(b"\0MODE\0")
        digest.update(str(stat_result.st_mode).encode("ascii"))
        if path.is_symlink():
            digest.update(b"\0LINK\0")
            digest.update(os.readlink(str(path)).encode("utf-8", "surrogateescape"))
        elif path.is_file():
            digest.update(b"\0FILE\0")
            with path.open("rb") as file:
                for chunk in iter(lambda: file.read(1024 * 1024), b""):
                    digest.update(chunk)
        elif path.is_dir():
            digest.update(b"\0DIR")
        else:
            digest.update(b"\0OTHER")
    return digest.hexdigest()


def current_revision(root):
    result = run_git(root, "rev-parse", "--verify", "HEAD", check=False)
    return result.stdout.strip() if result.returncode == 0 else None


def workspace_is_dirty(root):
    status = run_git(
        root,
        "status",
        "--porcelain=v1",
        "-z",
        "--untracked-files=all",
        binary=True,
    ).stdout
    return bool(status)


def index_matches_worktree(root):
    unmerged = run_git(
        root,
        "diff",
        "--name-only",
        "-z",
        "--diff-filter=U",
        binary=True,
    ).stdout
    if unmerged:
        return False, "병합되지 않은 Git index 항목이 있습니다."

    staged_present = run_git(
        root,
        "diff",
        "--cached",
        "--name-only",
        "-z",
        "--diff-filter=ACMRTUXB",
        binary=True,
    ).stdout
    staged_deleted = run_git(
        root,
        "diff",
        "--cached",
        "--name-only",
        "-z",
        "--diff-filter=D",
        binary=True,
    ).stdout

    index_entries = {}
    raw_entries = run_git(
        root,
        "ls-files",
        "--stage",
        "-z",
        binary=True,
    ).stdout
    for record in (item for item in raw_entries.split(b"\0") if item):
        header, separator, raw_path = record.partition(b"\t")
        fields = header.split()
        if not separator or len(fields) != 3 or fields[2] != b"0":
            continue
        index_entries[raw_path] = (fields[0].decode("ascii"), fields[1].decode("ascii"))

    for raw_path in (item for item in staged_present.split(b"\0") if item):
        relative = os.fsdecode(raw_path)
        path = root / relative
        entry = index_entries.get(raw_path)
        if entry is None or not path.exists():
            return False, "staged 파일과 작업 트리가 다릅니다: {}".format(relative)
        hash_result = run_git(
            root,
            "hash-object",
            "--path={}".format(relative),
            relative,
            check=False,
        )
        if hash_result.returncode != 0 or hash_result.stdout.strip() != entry[1]:
            return False, "staged 내용과 테스트할 파일이 다릅니다: {}".format(relative)
        if path.is_symlink():
            worktree_mode = "120000"
        elif path.is_file():
            worktree_mode = "100755" if path.stat().st_mode & 0o100 else "100644"
        else:
            worktree_mode = entry[0]
        if entry[0] != worktree_mode:
            return False, "staged 실행 권한과 작업 트리가 다릅니다: {}".format(relative)

    for raw_path in (item for item in staged_deleted.split(b"\0") if item):
        relative = os.fsdecode(raw_path)
        if (root / relative).exists():
            return False, "staged 삭제와 작업 트리가 다릅니다: {}".format(relative)
    return True, None


def plan_is_complete(state):
    plan = state.get("plan") or {}
    acceptance = plan.get("acceptance_criteria") or []
    steps = plan.get("steps") or []
    return bool(acceptance and steps)


def unresolved_failures(state):
    return [
        failure
        for failure in state.get("failures", [])
        if failure.get("resolved_by_plan_version") is None
    ]


def status_information(root, state=None):
    state = state if state is not None else load_state(root)
    if state is None:
        return {
            "active": False,
            "status": "inactive",
            "gate": False,
            "reason": "활성 작업이 없습니다.",
            "next": "파일을 바꿀 작업이라면 ./scripts/harness begin을 실행하세요.",
        }

    try:
        current_fingerprint = workspace_fingerprint(root)
    except HarnessError as exc:
        return {
            "active": True,
            "status": "invalid",
            "gate": False,
            "reason": str(exc),
            "next": "Git 상태와 하네스 설정을 복구하세요.",
            "task_id": state.get("task_id"),
        }

    common = {
        "active": True,
        "task_id": state.get("task_id"),
        "request": state.get("request"),
        "phase": state.get("phase"),
        "iteration": state.get("iteration"),
        "plan_version": state.get("plan_version"),
        "current_fingerprint": current_fingerprint,
        "last_log": state.get("last_log"),
        "harness_maintenance": bool(state.get("harness_maintenance")),
    }

    if state.get("phase") == "blocked":
        return dict(
            common,
            status="blocked",
            gate=False,
            reason=state.get("blocker") or "외부 입력이 필요합니다.",
            next="사용자 입력이나 권한을 받은 뒤 ./scripts/harness revise를 실행하세요.",
        )

    if not plan_is_complete(state):
        return dict(
            common,
            status="planning",
            gate=False,
            reason="완료 기준과 구현 단계가 있는 계획이 필요합니다.",
            next="./scripts/harness plan을 실행하세요.",
        )

    if unresolved_failures(state):
        return dict(
            common,
            status="failed",
            gate=False,
            reason="계획 수정으로 해소되지 않은 실패가 있습니다.",
            next="로그를 읽고 ./scripts/harness revise를 실행하세요.",
        )

    if state.get("phase") == "passed":
        pass_fingerprint = state.get("pass_fingerprint")
        results = state.get("results") or {}
        test_result = results.get("test") or {}
        verify_result = results.get("verify") or {}
        manual_checks = (state.get("plan") or {}).get("manual_checks") or []
        manual_fresh = all(
            check.get("evidence")
            and check["evidence"].get("fingerprint") == pass_fingerprint
            for check in manual_checks
        )
        evidence_valid = (
            pass_fingerprint
            and test_result.get("ok")
            and verify_result.get("ok")
            and test_result.get("fingerprint") == pass_fingerprint
            and verify_result.get("fingerprint") == pass_fingerprint
            and manual_fresh
        )
        index_ok, index_reason = index_matches_worktree(root)
        pass_revision = state.get("pass_revision")
        revision = current_revision(root)
        revision_ok = revision == pass_revision or not workspace_is_dirty(root)
        if (
            evidence_valid
            and current_fingerprint == pass_fingerprint
            and index_ok
            and revision_ok
        ):
            return dict(
                common,
                status="passed",
                gate=True,
                reason="현재 파일 지문에서 테스트와 검증이 모두 통과했습니다.",
                next="완료 결과와 실행한 검증을 보고하세요.",
            )
        reason = "마지막 통과 뒤 파일이 바뀌었거나 검증 증거가 불완전합니다."
        if not index_ok:
            reason = index_reason
        elif not revision_ok:
            reason = "검증 뒤 HEAD가 바뀌었지만 작업 트리에 미커밋 변경이 남아 있습니다."
        return dict(
            common,
            status="stale",
            gate=False,
            reason=reason,
            next="./scripts/harness revise 후 테스트와 검증을 다시 실행하세요.",
        )

    phase = state.get("phase")
    next_by_phase = {
        "planning": "./scripts/harness plan을 실행하세요.",
        "implementing": "구현을 마친 뒤 ./scripts/harness run test를 실행하세요.",
        "testing": "실행 중인 테스트 결과를 확인하세요.",
        "tested": "수동 증거를 기록한 뒤 ./scripts/harness run verify를 실행하세요.",
        "verifying": "실행 중인 검증 결과를 확인하세요.",
        "failed": "로그를 읽고 ./scripts/harness revise를 실행하세요.",
    }
    return dict(
        common,
        status=phase or "invalid",
        gate=False,
        reason="작업이 아직 최종 통과 상태가 아닙니다.",
        next=next_by_phase.get(phase, "하네스 상태를 복구하세요."),
    )


def begin_task(root, request, adopt_existing, harness_maintenance=False):
    load_config(root)
    with state_lock(root):
        previous = load_state(root)
        if previous is not None:
            previous_status = status_information(root, previous)
            if not previous_status.get("gate"):
                raise HarnessError(
                    "이전 작업이 통과하지 않았습니다. status를 확인하고 기존 작업을 마치세요."
                )
        if workspace_is_dirty(root) and not adopt_existing:
            raise HarnessError(
                "기존 변경이 있습니다. 검토 후 --adopt-existing으로 명시적으로 편입하세요."
            )
        fingerprint = workspace_fingerprint(root)
        state = {
            "schema_version": SCHEMA_VERSION,
            "task_id": uuid.uuid4().hex,
            "request": request.strip(),
            "adopted_existing": bool(adopt_existing),
            "harness_maintenance": bool(harness_maintenance),
            "phase": "planning",
            "iteration": 1,
            "plan_version": 1,
            "started_at": utc_now(),
            "updated_at": utc_now(),
            "base_revision": current_revision(root),
            "base_fingerprint": fingerprint,
            "plan": None,
            "revisions": [],
            "runs": [],
            "results": {"test": None, "verify": None},
            "failures": [],
            "pass_fingerprint": None,
            "pass_revision": None,
            "last_log": None,
            "blocker": None,
        }
        save_state(root, state)
        append_event(
            root,
            "task_begun",
            state,
            adopted_existing=bool(adopt_existing),
            fingerprint=fingerprint,
        )
    print("하네스 작업을 시작했습니다: {}".format(state["task_id"]))
    print("다음 단계: ./scripts/harness plan")


def record_plan(root, assumptions, acceptance, steps, manual_checks):
    assumptions = [value.strip() for value in assumptions if value.strip()]
    acceptance = [value.strip() for value in acceptance if value.strip()]
    steps = [value.strip() for value in steps if value.strip()]
    manual_checks = [value.strip() for value in manual_checks if value.strip()]
    with state_lock(root):
        state = load_state(root)
        if state is None:
            raise HarnessError("먼저 ./scripts/harness begin을 실행하세요.")
        if state.get("phase") != "planning":
            raise HarnessError("계획은 planning 상태에서만 처음 기록할 수 있습니다.")
        if not acceptance:
            raise HarnessError("검증 가능한 완료 기준을 하나 이상 입력하세요.")
        if not steps:
            raise HarnessError("구현 단계를 하나 이상 입력하세요.")
        if workspace_fingerprint(root) != state.get("base_fingerprint"):
            raise HarnessError(
                "begin 뒤 계획 전에 파일이 변경되었습니다. 변경을 되돌린 뒤 계획을 기록하세요."
            )
        state["plan"] = {
            "assumptions": assumptions,
            "acceptance_criteria": acceptance,
            "steps": steps,
            "manual_checks": [
                {
                    "id": "M{}".format(index),
                    "text": text,
                    "evidence": None,
                }
                for index, text in enumerate(manual_checks, start=1)
            ],
        }
        state["phase"] = "implementing"
        save_state(root, state)
        append_event(root, "plan_recorded", state)
    print("계획을 기록했습니다. 구현을 진행하세요.")


def log_name(stage, command_name):
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    safe_name = "".join(
        character if character.isalnum() or character in "-_" else "-"
        for character in command_name
    )
    return "{}-{}-{}.log".format(stamp, stage, safe_name)


def execute_commands(root, state, stage, commands):
    run_directory = state_directory(root) / "runs" / state["task_id"]
    run_directory.mkdir(parents=True, exist_ok=True)
    command_results = []

    for entry in commands:
        argv = entry["argv"]
        path = run_directory / log_name(stage, entry["name"])
        started_at = utc_now()
        display = shlex.join(argv)
        print("[{}] {}".format(entry["name"], display), flush=True)
        with path.open("w", encoding="utf-8") as log:
            log.write("$ " + display + "\n\n")
            log.flush()
            try:
                process = subprocess.Popen(
                    argv,
                    cwd=str(root),
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    bufsize=1,
                )
                assert process.stdout is not None
                for line in process.stdout:
                    sys.stdout.write(line)
                    sys.stdout.flush()
                    log.write(line)
                    log.flush()
                exit_code = process.wait()
            except OSError as exc:
                message = "명령을 시작할 수 없습니다: {}\n".format(exc)
                sys.stdout.write(message)
                log.write(message)
                exit_code = 127
        result = {
            "name": entry["name"],
            "argv": argv,
            "display": display,
            "started_at": started_at,
            "finished_at": utc_now(),
            "exit_code": exit_code,
            "log": str(path.relative_to(root)),
        }
        command_results.append(result)
        if exit_code != 0:
            break
    return command_results


def record_failure(state, stage, reason, command_results, fingerprint):
    failure = {
        "at": utc_now(),
        "stage": stage,
        "reason": reason,
        "plan_version": state.get("plan_version"),
        "iteration": state.get("iteration"),
        "fingerprint": fingerprint,
        "commands": command_results,
        "resolved_by_plan_version": None,
    }
    state.setdefault("failures", []).append(failure)
    state["phase"] = "failed"
    state["pass_fingerprint"] = None
    state["pass_revision"] = None
    return failure


def manual_evidence_is_fresh(state, fingerprint):
    checks = (state.get("plan") or {}).get("manual_checks") or []
    return all(
        check.get("evidence")
        and check["evidence"].get("fingerprint") == fingerprint
        for check in checks
    )


def run_stage(root, stage):
    config = load_config(root)
    expected_phase = "implementing" if stage == "test" else "tested"
    running_phase = "testing" if stage == "test" else "verifying"

    with state_lock(root):
        state = load_state(root)
        if state is None:
            raise HarnessError("활성 작업이 없습니다.")
        if state.get("phase") != expected_phase:
            raise HarnessError(
                "{} 단계는 {} 상태에서만 실행할 수 있습니다. 현재: {}".format(
                    stage, expected_phase, state.get("phase")
                )
            )
        index_ok, index_reason = index_matches_worktree(root)
        if not index_ok:
            raise HarnessError(index_reason)
        before = workspace_fingerprint(root)
        if stage == "verify":
            test_result = (state.get("results") or {}).get("test") or {}
            if not test_result.get("ok") or test_result.get("fingerprint") != before:
                failure = record_failure(
                    state,
                    "verify",
                    "테스트 뒤 파일이 변경되어 테스트 결과가 오래되었습니다.",
                    [],
                    before,
                )
                save_state(root, state)
                append_event(root, "stage_failed", state, failure=failure)
                print("테스트 뒤 파일이 변경되었습니다. 계획을 수정하고 다시 테스트하세요.")
                return 1
            if not manual_evidence_is_fresh(state, before):
                raise HarnessError(
                    "현재 파일 지문에 대한 수동 확인 증거가 없습니다. evidence를 먼저 실행하세요."
                )
        state["phase"] = running_phase
        save_state(root, state)
        append_event(root, "stage_started", state, stage=stage, fingerprint=before)
        task_id = state["task_id"]
        plan_version = state["plan_version"]

    command_results = execute_commands(
        root,
        state,
        stage,
        config["commands"][stage],
    )
    after = workspace_fingerprint(root)
    commands_ok = (
        len(command_results) == len(config["commands"][stage])
        and all(item["exit_code"] == 0 for item in command_results)
    )

    with state_lock(root):
        state = load_state(root)
        if state is None or state.get("task_id") != task_id:
            raise HarnessError("실행 중 하네스 작업이 바뀌었습니다.")
        if (
            state.get("phase") != running_phase
            or state.get("plan_version") != plan_version
        ):
            raise HarnessError(
                "실행 중 하네스 상태나 계획 버전이 바뀌어 결과를 반영하지 않았습니다."
            )
        result = {
            "stage": stage,
            "ok": bool(commands_ok and before == after),
            "fingerprint": after,
            "started_fingerprint": before,
            "started_at": command_results[0]["started_at"] if command_results else utc_now(),
            "finished_at": utc_now(),
            "commands": command_results,
        }
        state.setdefault("runs", []).append(result)
        state.setdefault("results", {})[stage] = result
        state["last_log"] = (
            command_results[-1]["log"] if command_results else state.get("last_log")
        )

        if not commands_ok:
            failure = record_failure(
                state,
                stage,
                "{} 명령이 0이 아닌 종료 코드로 끝났습니다.".format(stage),
                command_results,
                after,
            )
            save_state(root, state)
            append_event(root, "stage_failed", state, failure=failure)
            print("{} 실패. 로그를 확인한 뒤 revise를 실행하세요.".format(stage))
            return 1
        if before != after:
            failure = record_failure(
                state,
                stage,
                "{} 실행 중 버전 관리 대상 파일이 변경되었습니다.".format(stage),
                command_results,
                after,
            )
            save_state(root, state)
            append_event(root, "stage_failed", state, failure=failure)
            print("실행 중 파일 지문이 바뀌었습니다. revise 후 다시 실행하세요.")
            return 1

        if stage == "test":
            state["phase"] = "tested"
        else:
            state["phase"] = "passed"
            state["pass_fingerprint"] = after
            state["pass_revision"] = current_revision(root)
        save_state(root, state)
        append_event(root, "stage_passed", state, stage=stage, fingerprint=after)

    print("{} 통과.".format(stage))
    return 0


def revise_plan(root, cause, change):
    cause = cause.strip()
    change = change.strip()
    if not cause or not change:
        raise HarnessError("실패 원인과 계획 변경은 비어 있을 수 없습니다.")
    with state_lock(root):
        state = load_state(root)
        if state is None:
            raise HarnessError("활성 작업이 없습니다.")
        if state.get("phase") == "planning":
            raise HarnessError("첫 계획은 ./scripts/harness plan으로 기록하세요.")
        if state.get("phase") in ("testing", "verifying"):
            raise HarnessError("실행 중인 테스트나 검증이 끝난 뒤 계획을 수정하세요.")
        info = status_information(root, state)
        if info.get("gate"):
            raise HarnessError("이미 현재 파일 지문에서 통과했습니다. 새 작업은 begin으로 시작하세요.")
        pending_failures = unresolved_failures(state)
        if (
            pending_failures
            and workspace_fingerprint(root) != pending_failures[-1].get("fingerprint")
        ):
            raise HarnessError(
                "실패 뒤 계획 수정 전에 파일이 변경되었습니다. 변경을 되돌린 뒤 revise를 실행하세요."
            )
        previous_version = state.get("plan_version", 1)
        state["plan_version"] = previous_version + 1
        state["iteration"] = state.get("iteration", 1) + 1
        revision = {
            "at": utc_now(),
            "from_plan_version": previous_version,
            "to_plan_version": state["plan_version"],
            "cause": cause,
            "change": change,
            "fingerprint": workspace_fingerprint(root),
        }
        state.setdefault("revisions", []).append(revision)
        for failure in state.get("failures", []):
            if failure.get("resolved_by_plan_version") is None:
                failure["resolved_by_plan_version"] = state["plan_version"]
        for check in (state.get("plan") or {}).get("manual_checks") or []:
            check["evidence"] = None
        state["results"] = {"test": None, "verify": None}
        state["pass_fingerprint"] = None
        state["pass_revision"] = None
        state["blocker"] = None
        if state.get("phase") == "blocked" and state.get("blocked_from") == "planning":
            state["phase"] = "planning"
        else:
            state["phase"] = "implementing"
        state["blocked_from"] = None
        save_state(root, state)
        append_event(root, "plan_revised", state, revision=revision)
    print("계획을 버전 {}로 수정했습니다.".format(state["plan_version"]))


def record_evidence(root, check_id, result_text):
    check_id = check_id.strip()
    result_text = result_text.strip()
    if not check_id or not result_text:
        raise HarnessError("수동 확인 항목과 결과는 비어 있을 수 없습니다.")
    with state_lock(root):
        state = load_state(root)
        if state is None:
            raise HarnessError("활성 작업이 없습니다.")
        if state.get("phase") not in ("implementing", "tested"):
            raise HarnessError("수동 증거는 구현 중이거나 테스트 통과 뒤에 기록하세요.")
        checks = (state.get("plan") or {}).get("manual_checks") or []
        target = next((item for item in checks if item.get("id") == check_id), None)
        if target is None:
            raise HarnessError("수동 확인 항목 {}을 찾을 수 없습니다.".format(check_id))
        target["evidence"] = {
            "at": utc_now(),
            "result": result_text,
            "fingerprint": workspace_fingerprint(root),
        }
        save_state(root, state)
        append_event(root, "manual_evidence_recorded", state, check_id=check_id)
    print("{} 수동 확인 증거를 기록했습니다.".format(check_id))


def block_task(root, reason):
    reason = reason.strip()
    if not reason:
        raise HarnessError("차단 사유는 비어 있을 수 없습니다.")
    with state_lock(root):
        state = load_state(root)
        if state is None:
            raise HarnessError("활성 작업이 없습니다.")
        if state.get("phase") in ("testing", "verifying"):
            raise HarnessError("실행 중인 테스트나 검증이 끝난 뒤 BLOCKED로 전환하세요.")
        info = status_information(root, state)
        if info.get("gate"):
            raise HarnessError("이미 통과한 작업은 BLOCKED로 바꿀 수 없습니다.")
        state["blocked_from"] = state.get("phase")
        state["phase"] = "blocked"
        state["blocker"] = reason
        save_state(root, state)
        append_event(root, "task_blocked", state, reason=reason)
    print("작업을 BLOCKED로 기록했습니다. PASS가 아닙니다.")


def print_status(root, as_json=False):
    with state_read_lock(root):
        info = status_information(root)
    if as_json:
        print(json.dumps(info, ensure_ascii=False))
        return
    print("status: {}".format(info["status"]))
    print("gate: {}".format("PASS" if info.get("gate") else "FAIL"))
    if info.get("request"):
        print("task: {}".format(info["request"]))
    print("reason: {}".format(info["reason"]))
    print("next: {}".format(info["next"]))
    if info.get("last_log"):
        print("last_log: {}".format(info["last_log"]))


def gate(root):
    with state_read_lock(root):
        info = status_information(root)
    if info.get("gate"):
        print("HARNESS GATE: PASS")
        print(info["reason"])
        return 0
    print("HARNESS GATE: FAIL")
    print(info["reason"])
    print(info["next"])
    return 1


def hook_context(info, subagent=False):
    if subagent:
        text = (
            "이 저장소는 AGENTS.md와 에이전트 하네스를 사용합니다. "
            "서브에이전트는 맡은 범위의 결과, 실행 명령, 실패 증거를 메인 에이전트에 반환하고 "
            "하네스 상태 전이는 메인 에이전트에게 맡기세요."
        )
    else:
        text = (
            "이 저장소에서 파일을 바꾸는 작업은 AGENTS.md의 계획→구현→테스트→검증→"
            "실패 시 계획 수정 흐름을 따릅니다. 현재 하네스 상태: {}. 다음 행동: {}"
        ).format(info["status"], info["next"])
    return {
        "hookSpecificOutput": {
            "hookEventName": "SubagentStart" if subagent else "SessionStart",
            "additionalContext": text,
        }
    }


def edit_denial(reason):
    return {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }


def command_mentions_control_path(command, root):
    normalized = command.replace("\\", "/")
    if any(path in normalized for path in HARNESS_CONTROL_PATHS):
        return True

    try:
        tokens = shlex.split(command)
    except ValueError:
        return False

    root = root.resolve()
    root_text = str(root).replace("\\", "/")
    protected_relative = tuple(path.replace("\\", "/") for path in HARNESS_CONTROL_PATHS)
    protected_absolute = tuple((root / path).resolve() for path in HARNESS_CONTROL_PATHS)
    protected_names = {Path(path).name for path in HARNESS_CONTROL_PATHS}

    for token in tokens:
        values = [token]
        if token.startswith("-") and "=" in token:
            values.append(token.split("=", 1)[1])
        for value in values:
            value = value.replace("\\", "/")
            if not value or value.startswith("-"):
                continue
            if value == root_text:
                return True
            if value.startswith(root_text + "/"):
                relative = value[len(root_text) + 1 :]
            elif value.startswith("/"):
                continue
            else:
                relative = value
                while relative.startswith("./"):
                    relative = relative[2:]

            dynamic_indexes = [
                relative.find(marker)
                for marker in ("*", "?", "[", "{", "$(", chr(96))
                if marker in relative
            ]
            if dynamic_indexes:
                if any(
                    fnmatch.fnmatchcase(path, relative)
                    for path in protected_relative
                ):
                    return True
                static_prefix = relative[: min(dynamic_indexes)]
                if static_prefix and any(
                    path.startswith(static_prefix)
                    for path in protected_relative
                ):
                    return True
                continue

            if Path(relative).name in protected_names:
                return True
            candidate = (root / relative).resolve()
            if any(
                candidate == protected or candidate in protected.parents
                for protected in protected_absolute
            ):
                return True
    return False


def is_safe_single_command(command):
    quote = None
    escaped = False
    index = 0
    while index < len(command):
        character = command[index]
        if escaped:
            escaped = False
        elif quote == "'":
            if character == "'":
                quote = None
        elif quote == '"':
            if character == "\\":
                escaped = True
            elif character == '"':
                quote = None
            elif character == chr(96) or (
                character == "$"
                and index + 1 < len(command)
                and command[index + 1] == "("
            ):
                return False
        elif character == "\\":
            escaped = True
        elif character in ("'", '"'):
            quote = character
        elif character in "|;&<>\n()" or character == chr(96):
            return False
        elif (
            character == "$"
            and index + 1 < len(command)
            and command[index + 1] == "("
        ):
            return False
        index += 1
    return quote is None and not escaped


def is_harness_command(command, root, cwd):
    if not is_safe_single_command(command):
        return False
    try:
        tokens = shlex.split(command)
    except ValueError:
        return False
    if not tokens:
        return False
    executable = tokens[0].replace("\\", "/")
    absolute = str(root.resolve() / "scripts" / "harness").replace("\\", "/")
    if executable == absolute:
        return True
    return Path(cwd).resolve() == root.resolve() and executable in {
        "./scripts/harness",
        "scripts/harness",
    }


def is_trusted_read_executable(executable):
    if "/" not in executable:
        return True
    try:
        executable_path = Path(executable)
        resolved = executable_path.resolve()
        stated_parent = executable_path.parent.resolve()
    except OSError:
        return False
    trusted_directories = {
        Path("/bin").resolve(),
        Path("/usr/bin").resolve(),
        Path("/usr/local/bin").resolve(),
        Path("/opt/homebrew/bin").resolve(),
    }
    return (
        stated_parent in trusted_directories
        or resolved.parent in trusted_directories
    )


def is_abbreviated_option(token, full_option, minimum_length):
    base = token.split("=", 1)[0]
    return len(base) >= minimum_length and full_option.startswith(base)


def is_safe_read_only_bash(command):
    if not is_safe_single_command(command):
        return False
    try:
        tokens = shlex.split(command)
    except ValueError:
        return False
    if not tokens:
        return True
    if any(token == "--output" or token.startswith("--output=") for token in tokens):
        return False
    if not is_trusted_read_executable(tokens[0]):
        return False
    program = Path(tokens[0]).name
    if program in {
        "cat",
        "echo",
        "file",
        "head",
        "jq",
        "ls",
        "pwd",
        "realpath",
        "stat",
        "tail",
        "test",
        "wc",
        "which",
    }:
        return True
    if program == "rg":
        execution_options = ("--pre", "--hostname-bin")
        return not any(
            token == option or token.startswith(option + "=")
            for token in tokens[1:]
            for option in execution_options
        )
    if program == "command":
        return len(tokens) >= 2 and tokens[1] == "-v"
    if program == "find":
        forbidden = {
            "-delete",
            "-exec",
            "-execdir",
            "-fls",
            "-fprint",
            "-fprintf",
            "-ok",
            "-okdir",
        }
        return not any(token in forbidden for token in tokens[1:])
    if program != "git" or len(tokens) < 2:
        return False
    read_only_git = {
        "blame",
        "cat-file",
        "describe",
        "diff",
        "grep",
        "log",
        "ls-files",
        "name-rev",
        "rev-list",
        "rev-parse",
        "show",
        "status",
    }
    if tokens[1] in read_only_git:
        if any(
            is_abbreviated_option(token, "--ext-diff", 5)
            or is_abbreviated_option(token, "--textconv", 7)
            for token in tokens[2:]
        ):
            return False
        if tokens[1] == "grep" and any(
            token == "-O"
            or token.startswith("-O")
            or is_abbreviated_option(
                token,
                "--open-files-in-pager",
                len("--open"),
            )
            for token in tokens[2:]
        ):
            return False
        return True
    if tokens[1] == "branch":
        return all(
            token in {"branch", "--all", "--list", "--show-current", "-a", "-l"}
            for token in tokens[1:]
        )
    return False


def is_pass_delivery_bash(command):
    try:
        tokens = shlex.split(command)
    except ValueError:
        return False
    return (
        len(tokens) >= 2
        and Path(tokens[0]).name == "git"
        and tokens[1] in {"add", "commit", "push"}
        and is_safe_single_command(command)
    )


def handle_hook():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, OSError) as exc:
        print(
            json.dumps(
                {
                    "decision": "block",
                    "reason": "하네스 훅 입력을 읽지 못했습니다: {}".format(exc),
                },
                ensure_ascii=False,
            )
        )
        return 0

    try:
        hook_cwd = Path(payload.get("cwd") or os.getcwd()).resolve()
        root = find_repo_root(hook_cwd)
        with state_read_lock(root):
            info = status_information(root)
        event = payload.get("hook_event_name")

        if event == "SessionStart":
            output = hook_context(info)
        elif event == "SubagentStart":
            output = hook_context(info, subagent=True)
        elif event == "PreToolUse":
            tool_name = payload.get("tool_name")
            tool_input = payload.get("tool_input") or {}
            command = tool_input.get("command") or ""
            maintenance = info.get("harness_maintenance")
            if tool_name == "Bash":
                if is_harness_command(command, root, hook_cwd):
                    output = {}
                elif is_safe_read_only_bash(command):
                    output = {}
                elif command_mentions_control_path(command, root) and not maintenance:
                    output = edit_denial(
                        "하네스 제어 파일은 명시적인 --harness-maintenance 작업에서만 바꿀 수 있습니다."
                    )
                elif info["status"] == "implementing":
                    output = {}
                elif (
                    info.get("gate") and is_pass_delivery_bash(command)
                ):
                    output = {}
                else:
                    output = edit_denial(
                        "계획 없는 쓰기성 Bash 명령은 차단됩니다. "
                        "./scripts/harness begin과 plan을 먼저 실행하세요."
                    )
            elif (
                command_mentions_control_path(command, root)
                and not maintenance
            ):
                output = edit_denial(
                    "하네스 제어 파일은 명시적인 --harness-maintenance 작업에서만 바꿀 수 있습니다."
                )
            elif info["status"] == "implementing":
                output = {}
            elif info["status"] == "inactive":
                output = edit_denial(
                    "계획 없는 편집은 차단됩니다. ./scripts/harness begin과 plan을 먼저 실행하세요."
                )
            else:
                output = edit_denial(
                    "현재 하네스 상태는 {}입니다. {}".format(
                        info["status"], info["next"]
                    )
                )
        elif event == "Stop":
            if info["status"] in ("inactive", "blocked") or info.get("gate"):
                output = {}
            else:
                output = {
                    "decision": "block",
                    "reason": (
                        "하네스 gate가 아직 통과하지 않았습니다. 상태: {}. 이유: {} "
                        "다음 행동: {} 완료라고 답하지 말고 이 흐름을 계속하세요."
                    ).format(info["status"], info["reason"], info["next"]),
                }
        else:
            output = {}
    except (HarnessError, OSError) as exc:
        output = {
            "decision": "block",
            "reason": "하네스 상태를 확인할 수 없습니다: {}".format(exc),
        }

    print(json.dumps(output, ensure_ascii=False))
    return 0


def build_parser():
    parser = argparse.ArgumentParser(
        description="계획, 테스트, 검증 증거를 상태와 파일 지문에 묶습니다."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    begin = subparsers.add_parser("begin", help="새 하네스 작업 시작")
    begin.add_argument("--request", required=True, help="사용자 요청 요약")
    begin.add_argument(
        "--adopt-existing",
        action="store_true",
        help="하네스 설치 전에 시작된 변경을 정직하게 편입",
    )
    begin.add_argument(
        "--harness-maintenance",
        action="store_true",
        help="사용자가 명시적으로 요청한 하네스 제어 파일 변경",
    )

    plan = subparsers.add_parser("plan", help="가정, 완료 기준, 구현 단계 기록")
    plan.add_argument("--assumption", action="append", default=[])
    plan.add_argument("--acceptance", action="append", default=[], required=True)
    plan.add_argument("--step", action="append", default=[], required=True)
    plan.add_argument("--manual-check", action="append", default=[])

    run = subparsers.add_parser("run", help="설정된 테스트 또는 검증 실행")
    run.add_argument("stage", choices=("test", "verify"))

    revise = subparsers.add_parser("revise", help="실패 원인과 계획 변경 기록")
    revise.add_argument("--cause", required=True)
    revise.add_argument("--change", required=True)

    evidence = subparsers.add_parser("evidence", help="수동 확인 증거 기록")
    evidence.add_argument("--check", required=True)
    evidence.add_argument("--result", required=True)

    block = subparsers.add_parser("block", help="외부 입력이 필요한 차단 상태 기록")
    block.add_argument("--reason", required=True)

    status = subparsers.add_parser("status", help="현재 하네스 상태 표시")
    status.add_argument("--json", action="store_true")

    subparsers.add_parser("gate", help="현재 완료 게이트 판정")
    subparsers.add_parser("hook", help=argparse.SUPPRESS)
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    if args.command == "hook":
        return handle_hook()

    try:
        root = find_repo_root()
        if args.command == "begin":
            if not args.request.strip():
                raise HarnessError("요청 요약은 비어 있을 수 없습니다.")
            begin_task(
                root,
                args.request,
                args.adopt_existing,
                args.harness_maintenance,
            )
            return 0
        if args.command == "plan":
            record_plan(
                root,
                args.assumption,
                args.acceptance,
                args.step,
                args.manual_check,
            )
            return 0
        if args.command == "run":
            return run_stage(root, args.stage)
        if args.command == "revise":
            revise_plan(root, args.cause, args.change)
            return 0
        if args.command == "evidence":
            record_evidence(root, args.check, args.result)
            return 0
        if args.command == "block":
            block_task(root, args.reason)
            return 0
        if args.command == "status":
            print_status(root, args.json)
            return 0
        if args.command == "gate":
            return gate(root)
        parser.error("지원하지 않는 명령입니다.")
    except HarnessError as exc:
        print("HARNESS ERROR: {}".format(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
