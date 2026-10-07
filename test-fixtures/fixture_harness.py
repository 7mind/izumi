"""Shared process and evidence operations for published consumer fixtures."""

from concurrent.futures import ThreadPoolExecutor
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


class LaneCommand(TypedDict):
    scala: str
    platform: str
    cwd: str
    argv: list[str]


class LaneResult(TypedDict):
    scala: str
    platform: str
    actualExit: int


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
    shutil.copy2(__file__, destination.parent / 'fixture_harness.py')


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
