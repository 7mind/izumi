"""Shared process and evidence operations for published consumer fixtures."""

from concurrent.futures import ThreadPoolExecutor
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
from types import ModuleType
from typing import TextIO, TypedDict
import signal
import shutil
import subprocess

EXECUTION_EVENT_SCHEMA = 4


class LaneCommand(TypedDict):
    scala: str
    platform: str
    cwd: str
    argv: list[str]


class LaneResult(TypedDict):
    scala: str
    platform: str
    actualExit: int


class InputDigest(TypedDict):
    path: str
    sha256: str


class FrozenInput(InputDigest):
    frozen: str


def consumer_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    return parser


def scala_consumer_parser(*, multiple: bool) -> argparse.ArgumentParser:
    parser = consumer_parser()
    parser.add_argument('--scala-version', nargs='+' if multiple else None, choices=['3.9.0', '2.13.18'], required=True)
    return parser


def sha(path: Path | str) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_module(name: str, path: Path) -> ModuleType:
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None, path
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def freeze_driver(driver: Path | str, destination: Path) -> None:
    shutil.copy2(driver, destination)
    for helper in ['fixture_harness.py', 'fixture_framework.py']:
        shutil.copy2(Path(__file__).with_name(helper), destination.parent / helper)


def freeze_sources(sources: list[Path], base: Path, destination: Path) -> list[FrozenInput]:
    rows = []
    for source in sources:
        frozen = destination / source.relative_to(base)
        frozen.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, frozen)
        rows.append(dict(path=str(source), frozen=str(frozen), sha256=sha(source)))
    return rows


def write_sbt_project(build: Path, definition: str, sbt_version: str, plugins: str | None) -> None:
    project = build / 'project'
    project.mkdir(parents=True, exist_ok=True)
    (build / 'build.sbt').write_text(definition)
    (project / 'build.properties').write_text('sbt.version=' + sbt_version + '\n')
    if plugins is not None:
        (project / 'plugins.sbt').write_text(plugins)


def terminate_process(process: subprocess.Popen, grace_seconds: float) -> None:
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=grace_seconds)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait()


def wait_process(process: subprocess.Popen, timeout_seconds: float, grace_seconds: float) -> int:
    try:
        return process.wait(timeout=timeout_seconds)
    except subprocess.TimeoutExpired:
        terminate_process(process, grace_seconds)
        return 124


def run_process(argv: list[str], build: Path, log: TextIO, timeout_seconds: float, grace_seconds: float) -> int:
    process = subprocess.Popen(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    return wait_process(process, timeout_seconds, grace_seconds)


def run_lanes(commands: list[LaneCommand], out: Path, timeout_seconds: float) -> list[LaneResult]:
    def run(command):
        log_path = out / (command['scala'] + '-' + command['platform'] + '.log')
        with log_path.open('x') as log:
            child = subprocess.run(command['argv'], cwd=command['cwd'], stdout=log, stderr=subprocess.STDOUT, timeout=timeout_seconds)
        row = dict(scala=command['scala'], platform=command['platform'], actualExit=child.returncode)
        print(json.dumps(row), flush=True)
        return row

    with ThreadPoolExecutor(max_workers=2) as pool:
        return list(pool.map(run, commands))


def checked_lanes(commands: list[LaneCommand], out: Path, inputs: list[InputDigest], timeout_seconds: float, expected_exit: int) -> None:
    results = run_lanes(commands, out, timeout_seconds)
    changed = [row['path'] for row in inputs if sha(Path(row['path'])) != row['sha256']]
    (out / 'completion.json').write_text(json.dumps(dict(lanes=results, inputsChanged=changed), indent=2) + '\n')
    assert not changed and all(row['actualExit'] == expected_exit for row in results), results


def execution_stream(payload: str):
    envelopes = [json.loads(line) for line in payload.splitlines()]
    assert all(frame['schemaVersion'] == EXECUTION_EVENT_SCHEMA for frame in envelopes)
    messages = [frame['message'] for frame in envelopes]
    assert messages[-1]['kind'] == 'completed'
    events = [message for message in messages if message['kind'] == 'event']
    assert [int(event['sequence']) for event in events] == list(range(len(events)))
    return events, messages[-1]['outcome']
