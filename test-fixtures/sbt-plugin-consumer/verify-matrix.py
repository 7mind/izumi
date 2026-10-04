#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess


LANE_TIMEOUT_SECONDS = 900
SHUTDOWN_GRACE_SECONDS = 10
ALL_SUITES = "SuiteA SuiteB SuiteC SuiteD SuiteE"

CONTROLS = '''
val changeExternalInput = inputKey[Unit]("Change the owned untracked DI input")
val verifyNoRun = inputKey[Unit]("Verify a physical stock incremental no-op")
changeExternalInput := {
  val parsed = spaceDelimited("revision").parsed
  require(parsed.size == 1, "Expected exactly one revision")
  IO.write(file(sys.props("izumi.fixture.external-input")), parsed.head)
  streams.value.log.info("DI_EXTERNAL_INPUT revision=" + parsed.head)
}
verifyNoRun := {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1, "Expected exactly one case")
  require((file(sys.props("izumi.fixture.audit-root")) * "*").get().isEmpty, "A cached control executed")
  require((target.value / "test-reports" * "*.xml").get().isEmpty, "A cached control produced reports")
  streams.value.log.info("PLUGIN_STOCK_NOOP_OK case=" + parsed.head)
}
Test / testFrameworks += new TestFramework("izumi.fixtures.host.ForeignFramework")
Test / javaOptions += "-Dizumi.fixture.external-input=" + sys.props("izumi.fixture.external-input")
lazy val Integration = config("it").extend(Test)
lazy val pluginConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin).configs(Integration)
  .settings(inConfig(Integration)(Defaults.testSettings ++ distageTestSettings))
  .settings(Integration / unmanagedSourceDirectories := (Test / unmanagedSourceDirectories).value)
'''


def commands(sbt_version):
    quick = "test" if sbt_version == "2.0.9" else "testQuick"
    full = "testFull" if sbt_version == "2.0.9" else "test"
    sequence, expected = ["clean"], []

    def case(name, command, labels, acquisitions, revision):
        sequence.extend(["prepareFixture " + name, command])
        if labels:
            sequence.append(f"verifyFixture {name} {acquisitions} {labels}")
        else:
            sequence.append("verifyNoRun " + name)
        expected.append(dict(case=name, labels=labels.split(), acquisitions=acquisitions, revision=revision))

    case("full", full, ALL_SUITES + " ForeignSuite", 1, "alpha")
    if sbt_version == "2.0.9":
        sequence.append("show Test / definedTestDigests")
    case("conservative-repeat", quick, ALL_SUITES, 1, "alpha")
    sequence.append("changeExternalInput beta")
    case("changed-input", quick, ALL_SUITES, 1, "beta")
    case("selected", quick + " *SuiteC *SuiteD", "SuiteC SuiteD", 1, "beta")
    case("wildcard", quick + " *SuiteD", "SuiteD", 1, "beta")
    case("excluded", quick + " *Suite* -*SuiteC", "SuiteA SuiteB SuiteD SuiteE", 1, "beta")
    case("foreign-cached", quick + " *ForeignSuite", "", 0, "beta")
    case("foreign-explicit", "testOnly *ForeignSuite", "ForeignSuite", 0, "beta")
    case("foreign-cached-again", quick + " *ForeignSuite", "", 0, "beta")
    sequence.append('set Test / testOptions += Tests.Exclude(Seq("izumi.fixtures.host.SuiteC"))')
    case("configured-exclusion", quick, "SuiteA SuiteB SuiteD SuiteE", 1, "beta")
    sequence.append('set Test / testOptions ~= (_.filterNot(_.isInstanceOf[Tests.Exclude]))')
    sequence.append("set Test / fork := true")
    case("fork-full", full, ALL_SUITES + " ForeignSuite", 1, "beta")
    case("fork-selected", quick + " *SuiteC *SuiteD", "SuiteC SuiteD", 1, "beta")
    sequence.append("changeExternalInput gamma")
    case("fork-changed-input", quick, ALL_SUITES, 1, "gamma")
    case("custom-selected", "Integration / testOnly *SuiteC *SuiteD", "SuiteC SuiteD", 1, "gamma")
    case("custom-repeat", "Integration / testQuick *SuiteD", "SuiteD", 1, "gamma")
    sequence.extend(["show Test / testFrameworks", "show Test / dependencyClasspath", "show Test / fullClasspath",
                     "show Integration / testFrameworks", "show Integration / distageTargetId"])
    return sequence, expected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-version", required=True)
    parser.add_argument("--sbt-version", nargs="+", required=True, choices=["1.13.0", "2.0.9"])
    parser.add_argument("--scala-version", nargs="+", required=True, choices=["3.9.0", "2.13.18", "2.12.21"])
    parser.add_argument("--evidence-dir", type=Path, required=True)
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    fixture = root / "test-fixtures/host-sharing-consumer"
    evidence = arguments.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    sources = [Path(__file__).resolve(), fixture / "build.sbt", *sorted((fixture / "src").rglob("*.scala"))]
    rows = []
    for source in sources:
        copy = evidence / "sources" / source.relative_to(root)
        copy.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, copy)
        rows.append(dict(path=str(source), frozen=str(copy), sha256=hashlib.sha256(source.read_bytes()).hexdigest()))
    (evidence / "inputs.json").write_text(json.dumps(dict(sources=rows), indent=2) + "\n")
    outcomes = []
    for sbt_version in arguments.sbt_version:
        for scala_version in arguments.scala_version:
            lane = evidence / f"sbt{sbt_version}-scala{scala_version}"
            build = lane / "build"
            build.mkdir(parents=True)
            for row in rows:
                source = Path(row["path"])
                if source == Path(__file__).resolve():
                    continue
                target = build / source.relative_to(fixture)
                target.parent.mkdir(parents=True, exist_ok=True)
                text = Path(row["frozen"]).read_text()
                if source.name == "build.sbt":
                    start = text.index("Test / testFrameworks :=")
                    end = text.index("Test / javaOptions +=", start)
                    text = text[:start] + text[end:] + CONTROLS
                elif source.name == "FixturePlugin.scala":
                    before = 'new SharedResource(UUID.randomUUID().toString, Paths.get(sys.props("izumi.fixture.audit-root")))'
                    assert text.count(before) == 1
                    text = text.replace(before, 'new SharedResource(new String(Files.readAllBytes(Paths.get(sys.props("izumi.fixture.external-input"))), StandardCharsets.UTF_8).trim + "-" + UUID.randomUUID().toString, Paths.get(sys.props("izumi.fixture.audit-root")))')
                target.write_text(text)
            (build / "project").mkdir()
            (build / "project/build.properties").write_text("sbt.version=" + sbt_version + "\n")
            (build / "project/plugins.sbt").write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + arguments.artifact_version + '")\n')
            external = lane / "external-input.txt"
            external.write_text("alpha")
            sequence, expected = commands(sbt_version)
            if sbt_version == "2.0.9":
                sequence.insert(0, 'set Global / localCacheDirectory := file("' + str(lane / "local-cache") + '")')
            invocation = 'task_sbt_version="$1"; shift; exec sbt --server --sbt-version "$task_sbt_version" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv = ["direnv", "exec", str(root), "sh", "-c", invocation, "plugin-consumer", sbt_version,
                    "-Dizumi.fixture.scala-version=" + scala_version, "-Dizumi.fixture.version=" + arguments.artifact_version,
                    "-Dizumi.fixture.audit-root=" + str(build / "target/body-audit"), "-Dizumi.fixture.captures=" + str(lane / "cases"),
                    "-Dizumi.fixture.external-input=" + str(external), *sequence]
            (lane / "commands.json").write_text(json.dumps(dict(cwd=str(build), argv=argv, expected=expected), indent=2) + "\n")
            print("PLUGIN_LANE_START " + str(lane), flush=True)
            with (lane / "run.log").open("x") as log:
                process = subprocess.Popen(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                try:
                    code = process.wait(timeout=LANE_TIMEOUT_SECONDS)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=SHUTDOWN_GRACE_SECONDS)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
                    code = 124
                log.write("\nEXIT " + str(code) + "\n")
            raw = (lane / "run.log").read_text()
            failures = []
            for row in expected:
                name, labels = row["case"], row["labels"]
                marker = (f"TARGET_BOOTSTRAP_HOST_OK case={name} suites={len(labels)} bodies={len(labels) * 3} reported={len(labels) * 3} acquired={row['acquisitions']} released={row['acquisitions']}"
                          if labels else "PLUGIN_STOCK_NOOP_OK case=" + name)
                if raw.count(marker + "\n") != 1:
                    failures.append("Missing unique case marker: " + marker)
                if row["acquisitions"]:
                    records = list((lane / "cases" / name / "body-audit").glob("*.acquire"))
                    if len(records) != row["acquisitions"] or any(not record.read_text().startswith(row["revision"] + "-") for record in records):
                        failures.append("DI acquisition did not consume current external input: " + name)
            for diagnostic in ["RejectedExecutionException:", "NoClassDefFoundError:", "ClassNotFoundException:", "Exception in thread "]:
                if diagnostic in raw:
                    failures.append("Unexpected runtime diagnostic: " + diagnostic)
            if "DISTAGE_CACHE_DECISION suite=izumi.fixtures.host.SuiteC decision=rerun reason=untracked-input-closure" not in raw:
                failures.append("Explicit conservative cache decision missing")
            for row in rows:
                assert hashlib.sha256(Path(row["path"]).read_bytes()).hexdigest() == row["sha256"]
            digests = re.findall(r'(izumi\.fixtures\.host\.Suite[A-E]) -> (sha256-[a-f0-9]+/[0-9]+)', raw)
            outcome = dict(sbt=sbt_version, scala=scala_version, actualExit=code, expectedCases=len(expected), validationFailures=failures,
                           stockDigestPairs=digests, distinctStockSuiteDigests=len(digests) == 5 and len(set(d for _, d in digests)) == 5)
            (lane / "completion.json").write_text(json.dumps(outcome, indent=2) + "\n")
            outcomes.append(outcome)
            print(json.dumps(outcome), flush=True)
            if code or failures:
                (evidence / "completion.json").write_text(json.dumps(dict(exit=1, outcomes=outcomes), indent=2) + "\n")
                raise SystemExit(1)
    (evidence / "completion.json").write_text(json.dumps(dict(exit=0, outcomes=outcomes), indent=2) + "\n")


if __name__ == "__main__":
    main()
