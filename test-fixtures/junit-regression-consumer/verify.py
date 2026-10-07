#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time
import xml.etree.ElementTree as ET


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--artifact-version", required=True)
    args = parser.parse_args()
    fixture = Path(__file__).resolve().parent
    output = args.output.resolve()
    output.mkdir()
    sources = [path for path in fixture.rglob("*") if path.is_file() and path.suffix in {".sbt", ".scala", ".properties", ".py"}]
    frozen = {str(path): digest(path) for path in sources}
    results = []
    for compiler in ("2.13.18", "3.9.0"):
        lane = output / compiler
        build = lane / "build"
        build.mkdir(parents=True)
        for source in sources:
            target = build / source.relative_to(fixture)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
        argv = [
            "env", "-u", "CI", "direnv", "exec", str(args.repo.resolve()), "sh", "-c",
            'exec sbt --server -java-home "$JDK21" -batch -J-Xmx6G "$@"', "junit-regression",
            "-Dizumi.fixture.scala-version=" + compiler,
            "-Dizumi.fixture.version=" + args.artifact_version,
            "-Dizumi.fixture.captures=" + str(lane / "reports"),
            'set Global / localCacheDirectory := file("' + str(lane / "sbt-task-cache") + '")',
            "checks/Test/testFull", "parallel/captureJUnit", "checks/captureJUnit",
            "show checks/Test/definedTests", "show parallel/Test/definedTests",
            "show checks/Test/fullClasspath", "show parallel/Test/fullClasspath",
        ]
        (lane / "command.json").write_text(json.dumps({"cwd": str(build), "argv": argv, "inputSha256": frozen}, indent=2) + "\n")
        started = time.monotonic()
        with (lane / "run.log").open("w") as log:
            process = subprocess.Popen(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = process.wait(timeout=900)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                code = 124
        completion = {"actualExit": code, "elapsedSeconds": round(time.monotonic() - started, 3), "inputsChanged": [path for path, expected in frozen.items() if digest(Path(path)) != expected]}
        (lane / "completion.json").write_text(json.dumps(completion, indent=2) + "\n")
        assert code == 0 and not completion["inputsChanged"], completion
        reports = {}
        for project, suite, count in (("parallel", "ParallelSleepSuite", 4), ("checks", "JUnitXmlRegressionTest", 1)):
            xml = lane / "reports" / project / ("TEST-izumi.distage.testkit.reporting." + suite + ".xml")
            root = ET.parse(xml).getroot()
            cases = root.findall("testcase")
            assert int(root.get("tests")) == len(cases) == count
            assert all(case.find(tag) is None for case in cases for tag in ("failure", "error", "skipped"))
            reports[project] = {"sha256": digest(xml), "cases": [{"name": case.get("name"), "seconds": float(case.get("time"))} for case in cases]}
        assert {case["name"] for case in reports["parallel"]["cases"]} == {"intra-suite parallel sleeps should parallel sleep test " + str(index) for index in range(1, 5)}
        assert all(case["seconds"] >= 2.0 for case in reports["parallel"]["cases"])
        assert reports["checks"]["cases"][0]["name"] == "intra-suite parallel tests must each be reported in JUnit XML with non-zero per-test time"
        log = (lane / "run.log").read_text()
        assert log.count("JUNIT_PARALLEL_DURATION_OK name=") == 4
        assert log.count("JUNIT_REPORTER_PIPELINE_OK project=") == 2
        assert not any(vendor in log for vendor in ("org.scalatest", "org.scalactic", "org.scalatestplus"))
        results.append({"compiler": compiler, "reports": reports, **completion})
    (output / "qualified-junit-regression.json").write_text(json.dumps({"lanes": results, "inputSha256": frozen}, indent=2) + "\n")
    print("JUNIT_REGRESSION_MIGRATION_OK compilers=2 cases=2 parallelBodies=8 durationChecks=8")


if __name__ == "__main__":
    main()
