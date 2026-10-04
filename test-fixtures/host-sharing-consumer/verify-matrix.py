#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def commands(sbt_version):
    full = "testFull" if sbt_version == "2.0.9" else "test"
    all_suites = "SuiteA SuiteB SuiteC SuiteD SuiteE"
    selected = "testOnly izumi.fixtures.host.SuiteC izumi.fixtures.host.SuiteD"
    sequence = ["clean"]
    expected = []

    def case(name, request, labels, acquisitions):
        sequence.extend([f"prepareFixture {name}", request, f"verifyFixture {name} {acquisitions} {labels}"])
        count = len(labels.split()) * 3
        expected.append(dict(case=name, suites=len(labels.split()), bodies=count, reported=count, acquired=acquisitions, released=acquisitions))

    case("full", full, all_suites, 1)
    case("selected", selected, "SuiteC SuiteD", 1)
    case("repeated", selected, "SuiteC SuiteD", 1)
    sequence.append("set Test / parallelExecution := false")
    case("sequential", full, all_suites, 1)
    case("wildcard", "testOnly *SuiteD", "SuiteD", 1)
    case("excluded", "testOnly izumi.fixtures.host.Suite* -izumi.fixtures.host.SuiteC", "SuiteA SuiteB SuiteD SuiteE", 1)
    sequence.extend([
        "set Test / parallelExecution := true",
        'set Test / testFrameworks := Seq(new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"), new TestFramework("izumi.fixtures.host.ForeignFramework"))',
    ])
    case("mixed-framework", "testOnly izumi.fixtures.host.Suite* izumi.fixtures.host.ForeignSuite", all_suites + " ForeignSuite", 1)
    sequence.append('set Test / testFrameworks := Seq(new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"))')
    sequence.append("set Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))")
    case("limit-one", full, all_suites, 1)
    sequence.append("set Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 2))")
    case("limit-two", full, all_suites, 1)
    sequence.append("set Test / fork := true")
    case("fork-selected", selected, "SuiteC SuiteD", 1)
    case("fork-full", full, all_suites, 1)
    grouping = 'set Test / testGrouping := { val definitions = (Test / definedTests).value.filter(_.name.startsWith("izumi.fixtures.host.Suite")); val left = Set("izumi.fixtures.host.SuiteA", "izumi.fixtures.host.SuiteC"); val options = ForkOptions().withRunJVMOptions((Test / javaOptions).value.toVector); Seq(new Tests.Group("left", definitions.filter(test => left.contains(test.name)), Tests.SubProcess(options)), new Tests.Group("right", definitions.filterNot(test => left.contains(test.name)), Tests.SubProcess(options))) }'
    if sbt_version == "2.0.9":
        grouping = grouping.replace("testGrouping := {", "testGrouping := Def.uncached {")
    sequence.append(grouping)
    case("fork-two-groups", full, all_suites, 2)
    sequence.extend(["show Test / dependencyClasspath", "show Test / fullClasspath"])
    return sequence, expected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-version", required=True)
    parser.add_argument("--sbt-version", nargs="+", required=True, choices=["1.13.0", "2.0.9"])
    parser.add_argument("--scala-version", nargs="+", required=True, choices=["3.9.0", "2.13.18", "2.12.21"])
    parser.add_argument("--evidence-dir", type=Path, required=True)
    arguments = parser.parse_args()
    fixture = Path(__file__).resolve().parent
    root = fixture.parent.parent
    evidence = arguments.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    sources = [fixture / "build.sbt", fixture / "project/build.properties", Path(__file__).resolve()]
    sources.extend(sorted((fixture / "src").rglob("*.scala")))
    source_records = []
    for source in sources:
        target = evidence / "fixture-sources" / source.relative_to(fixture)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        source_records.append(dict(path=str(source), frozen=str(target), sha256=hashlib.sha256(source.read_bytes()).hexdigest()))
    processors = int(subprocess.check_output(["nproc"], text=True).strip())
    (evidence / "inputs.json").write_text(json.dumps(dict(sources=source_records, processors=processors, scope="Target-only SBT sharing/reporting process fixture; published dependency, no host substitution"), indent=2) + "\n")
    outcomes = []
    for sbt_version in arguments.sbt_version:
        for scala_version in arguments.scala_version:
            lane = evidence / f"sbt{sbt_version}-scala{scala_version}"
            build = lane / "build"
            build.mkdir(parents=True)
            for source in source_records:
                relative = Path(source["path"]).relative_to(fixture)
                if relative == Path("verify-matrix.py"):
                    continue
                target = build / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source["frozen"], target)
            (build / "project/build.properties").write_text(f"sbt.version={sbt_version}\n")
            sequence, expected = commands(sbt_version)
            if sbt_version == "2.0.9":
                sequence.insert(0, 'set Global / localCacheDirectory := file("' + str(lane / "local-cache") + '")')
            invocation = 'task_sbt_version="$1"; shift; exec sbt --server --sbt-version "$task_sbt_version" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv = ["direnv", "exec", str(root), "sh", "-c", invocation, "host-sharing", sbt_version,
                    f"-Dizumi.fixture.scala-version={scala_version}", f"-Dizumi.fixture.version={arguments.artifact_version}",
                    "-Dizumi.fixture.audit-root=" + str(build / "target/body-audit"), "-Dizumi.fixture.captures=" + str(lane / "cases"), *sequence]
            (lane / "commands.json").write_text(json.dumps(dict(cwd=str(build), argv=argv, sbtVersion=sbt_version, compiler=scala_version, commands=sequence, expectedCases=expected), indent=2) + "\n")
            log = lane / "run.log"
            print(f"HOST_SHARING_LANE_START sbt={sbt_version} scala={scala_version} log={log}", flush=True)
            with log.open("x") as output:
                result = subprocess.run(argv, cwd=build, stdout=output, stderr=subprocess.STDOUT, check=False)
                output.write(f"\nEXIT {result.returncode}\n")
            for source in source_records:
                assert hashlib.sha256(Path(source["path"]).read_bytes()).hexdigest() == source["sha256"], source["path"]
            raw = log.read_text()
            failures = []
            for row in expected:
                marker = "TARGET_BOOTSTRAP_HOST_OK " + " ".join(key + "=" + str(value) for key, value in row.items())
                if raw.count(marker) != 1:
                    failures.append(dict(reason="case marker", marker=marker, actual=raw.count(marker)))
            for diagnostic in ["RejectedExecutionException:", "NoClassDefFoundError:", "ClassNotFoundException:", "Exception in thread "]:
                if diagnostic in raw:
                    failures.append(dict(reason="uncaught callback/classloader/thread diagnostic", marker=diagnostic))
            row = dict(sbt=sbt_version, compiler=scala_version, exit=result.returncode, expectedCases=len(expected), validationFailures=failures, inputsUnchanged=True)
            (lane / "completion.json").write_text(json.dumps(row, indent=2) + "\n")
            outcomes.append(row)
            print(json.dumps(row), flush=True)
            if result.returncode != 0 or failures:
                (evidence / "completion.json").write_text(json.dumps(dict(exit=1, outcomes=outcomes), indent=2) + "\n")
                raise SystemExit(1)
    (evidence / "completion.json").write_text(json.dumps(dict(exit=0, outcomes=outcomes), indent=2) + "\n")
    print(f"HOST_SHARING_MATRIX_OK lanes={len(outcomes)} cases={len(outcomes) * 12}", flush=True)


if __name__ == "__main__":
    main()
