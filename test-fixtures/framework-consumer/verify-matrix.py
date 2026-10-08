#!/usr/bin/env python3
import argparse
from pathlib import Path
import subprocess


def commands():
    full = "testFull"
    all_suites = "SuiteA SuiteB SuiteC SuiteD SuiteE"
    selected = "testOnly izumi.fixtures.bootstrap.SuiteA izumi.fixtures.bootstrap.SuiteC"
    return "; ".join([
        "clean",
        f"prepareFixture {all_suites}", full, f"verifyFixture {all_suites}",
        "prepareFixture SuiteA SuiteC", selected, "verifyFixture SuiteA SuiteC",
        "prepareFixture SuiteA SuiteC", selected, "verifyFixture SuiteA SuiteC",
        "set Test / parallelExecution := false",
        f"prepareFixture {all_suites}", full, f"verifyFixture {all_suites}",
        "prepareFixture SuiteB", "testOnly *SuiteB", "verifyFixture SuiteB",
        "set Test / parallelExecution := true",
        "set Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))",
        f"prepareFixture {all_suites}", full, f"verifyFixture {all_suites}",
        "set Test / fork := true",
        "prepareFixture SuiteA SuiteC", selected, "verifyFixture SuiteA SuiteC",
        f"prepareFixture {all_suites}", full, f"verifyFixture {all_suites}",
        "show Test / dependencyClasspath",
    ])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-version", required=True)
    parser.add_argument("--sbt-version", nargs="+", required=True, choices=["2.0.9"])
    parser.add_argument("--scala-version", nargs="+", required=True, choices=["3.9.0", "2.13.18"])
    parser.add_argument("--evidence-dir", type=Path, required=True)
    arguments = parser.parse_args()
    fixture = Path(__file__).resolve().parent
    evidence = arguments.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    invocation = 'exec sbt --server --sbt-version "$1" -java-home "$JDK21" -batch -J-Xmx6G "-Dizumi.fixture.scala-version=$2" "-Dizumi.fixture.version=$3" "-Dizumi.fixture.audit-root=$4" "$5"'
    lanes = 0
    for sbt_version in arguments.sbt_version:
        for scala_version in arguments.scala_version:
            command = ["direnv", "exec", "../..", "sh", "-c", invocation, "sh", sbt_version, scala_version, arguments.artifact_version, str(fixture / "target/body-audit"), commands()]
            log = evidence / f"2b-bootstrap-host-sbt{sbt_version}-scala{scala_version}.log"
            print(f"FRAMEWORK_HOST_LANE_START sbt={sbt_version} scala={scala_version} log={log}", flush=True)
            with log.open("w") as output:
                result = subprocess.run(command, cwd=fixture, stdout=output, stderr=subprocess.STDOUT, check=False)
            if result.returncode != 0:
                raise SystemExit(f"Host lane failed with exit {result.returncode}; see {log}")
            markers = [line for line in log.read_text().splitlines() if "PUBLISHED_FRAMEWORK_HOST_OK" in line]
            if len(markers) != 8:
                raise SystemExit(f"Expected eight independently verified host cases, observed {len(markers)}; see {log}")
            lanes += 1
            print(f"FRAMEWORK_HOST_LANE_OK sbt={sbt_version} scala={scala_version} cases={len(markers)}", flush=True)
    print(f"FRAMEWORK_HOST_MATRIX_OK lanes={lanes} cases={lanes * 8}", flush=True)


if __name__ == "__main__":
    main()
