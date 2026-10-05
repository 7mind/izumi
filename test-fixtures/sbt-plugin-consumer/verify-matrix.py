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

FORK_SENTINEL_SOURCE = '''package izumi.fixtures.host
final class ForkSentinelSuite extends izumi.distage.testkit.runner.spec.AnyWordSpec {
  require(System.getProperty("izumi.fixture.inspection-sentinel") == "owned-value", "INSPECTION_FORK_SENTINEL_MISSING")
  println("PLUGIN_INSPECTION_FORK_SENTINEL_OK pid=" + ProcessHandle.current().pid() + " value=owned-value")
  "must not execute" in { throw new IllegalStateException("INSPECTION_EXECUTED_BODY") }
}
'''

CONTROLS = '''
val changeExternalInput = inputKey[Unit]("Change the owned untracked DI input")
val verifyNoRun = inputKey[Unit]("Verify a physical stock incremental no-op")
val verifyInspectionParent = inputKey[Unit]("Verify the inspection fork sentinel is absent from the SBT process")
val changeScannedImplementation = taskKey[Unit]("Edit an implementation reached only through plugin scanning")
val changeSuiteClass = taskKey[Unit]("Edit a suite class before incremental execution")
changeScannedImplementation := {
  val source = baseDirectory.value / "src/test/scala/izumi/fixtures/host/FixturePlugin.scala"
  val before = "private def implementationRevision: String = " + '"' + "one" + '"'
  val text = IO.read(source)
  require(text.split(java.util.regex.Pattern.quote(before),-1).length == 2,"Scanned implementation edit precondition differs")
  IO.write(source,text.replace(before,before.replace("one","two")))
  streams.value.log.info("PLUGIN_HISTORY_EDIT_OK kind=scanned-implementation revision=two")
}
changeSuiteClass := {
  val source = baseDirectory.value / "src/test/scala/izumi/fixtures/host/SuiteC.scala"
  val before = "def marker3: Int = 3"
  val text = IO.read(source)
  require(text.split(java.util.regex.Pattern.quote(before),-1).length == 2,"Suite edit precondition differs")
  IO.write(source,text.replace(before,"def marker3: Int = 4"))
  streams.value.log.info("PLUGIN_HISTORY_EDIT_OK kind=suite-class revision=4")
}
verifyInspectionParent := {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1, "Expected one inspection case name")
  require(System.getProperty("izumi.fixture.inspection-sentinel") == null, "Inspection sentinel leaked into the SBT process")
  streams.value.log.info("PLUGIN_INSPECTION_PARENT_OK pid=" + ProcessHandle.current().pid() + " sentinel=absent case=" + parsed.head)
}
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
  val capture = file(sys.props("izumi.fixture.captures")) / parsed.head
  require(!capture.exists(), "Empty fixture capture must be new")
  IO.copyDirectory(file(sys.props("izumi.fixture.audit-root")), capture / "body-audit")
  IO.createDirectory(capture / "test-reports")
  streams.value.log.info("PLUGIN_STOCK_NOOP_OK case=" + parsed.head)
}
val verifySelectedFixture = inputKey[Unit]("Reconcile one physically selected body with its host report")
verifySelectedFixture := {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1, "Expected one selected case name")
  val audit = file(sys.props("izumi.fixture.audit-root"))
  val bodies = (audit * "*.body").get().map(path => IO.read(path).trim.split("\\t", -1).toSeq)
  val acquired = (audit * "*.acquire").get().map(IO.read(_).trim)
  val released = (audit * "*.release").get().map(IO.read(_).trim)
  require(bodies.size == 1 && bodies.head.take(2) == Seq("izumi.fixtures.host.SuiteC", "1"), "Physical selected ID differs")
  require(acquired.size == 1 && acquired == released && bodies.head(2) == acquired.head, "Selected resource lifetime differs")
  val reports = (target.value / "test-reports" * "*.xml").get()
  val factory = DocumentBuilderFactory.newInstance()
  val cases = reports.flatMap { report =>
    val document = factory.newDocumentBuilder().parse(report)
    require(Seq("failure", "error", "skipped").forall(name => document.getElementsByTagName(name).getLength == 0), "Selected report failed")
    val nodes = document.getElementsByTagName("testcase")
    (0 until nodes.getLength).map { index =>
      val node = nodes.item(index).asInstanceOf[org.w3c.dom.Element]
      node.getAttribute("classname") -> node.getAttribute("name")
    }
  }
  require(cases == Seq("izumi.fixtures.host.SuiteC" -> "equal display name should first"), "Selected host identities differ")
  val capture = file(sys.props("izumi.fixture.captures")) / parsed.head
  require(!capture.exists(), "Selected fixture capture must be new")
  IO.copyDirectory(audit, capture / "body-audit")
  IO.copyDirectory(target.value / "test-reports", capture / "test-reports")
  streams.value.log.info("PLUGIN_SELECTED_OK case=" + parsed.head + " bodies=1 reported=1 acquired=1 released=1")
}
Test / testFrameworks += new TestFramework("izumi.fixtures.host.ForeignFramework")
Test / javaOptions += "-Dizumi.fixture.external-input=" + sys.props("izumi.fixture.external-input")
lazy val Integration = config("it").extend(Test)
lazy val InspectionOnly = config("inspection").extend(Test)
lazy val pluginConsumer = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin).configs(Integration, InspectionOnly)
  .settings(inConfig(Integration)(Defaults.testSettings ++ distageTestSettings))
  .settings(inConfig(InspectionOnly)(Defaults.testSettings ++ distageTestSettings))
  .settings(Integration / unmanagedSourceDirectories := (Test / unmanagedSourceDirectories).value)
  .settings(InspectionOnly / fork := true, InspectionOnly / javaOptions := Seq("-Dizumi.fixture.inspection-sentinel=owned-value"))
'''


REJECTED_FILTER_CONTROL = r'''
val verifyRejectedAxisFilter = inputKey[Unit]("Verify an empty activation filter fails before provisioning")
verifyRejectedAxisFilter := {
  val parsed = spaceDelimited("case").parsed
  require(parsed.size == 1, "Expected one rejected filter case")
  val result = (Test / testOnly).toTask(" *SuiteC -- --axis-filter \"{\\\"axis\\\":\\\"repo\\\",\\\"value\\\":\\\"dummy\\\"}\"").result.value
  require(result.toEither.isLeft, "A filter matching no tests did not fail")
  val audit = file(sys.props("izumi.fixture.audit-root"))
  require((audit * "*").get().isEmpty, "Rejected filter acquired resources or executed bodies")
  val reports = (target.value / "test-reports" * "*.xml").get()
  require(reports.size == 1, "Rejected filter must retain one suite error report")
  val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(reports.head)
  val cases = document.getElementsByTagName("testcase")
  val errors = document.getElementsByTagName("error")
  require(cases.getLength == 1 && errors.getLength == 1, "Rejected filter suite error count differs")
  require(Seq("failure", "skipped").forall(name => document.getElementsByTagName(name).getLength == 0), "Rejected filter has an unexpected outcome")
  require(cases.item(0).asInstanceOf[org.w3c.dom.Element].getAttribute("classname") == "izumi.fixtures.host.SuiteC", "Rejected filter suite identity differs")
  require(cases.item(0).asInstanceOf[org.w3c.dom.Element].getAttribute("name") == "(It is not a test it is a sbt.testing.SuiteSelector)", "Rejected filter suite selector differs")
  require(errors.item(0).getTextContent.contains("Selection matched no tests"), "Rejected filter failed for an unexpected reason")
  val capture = file(sys.props("izumi.fixture.captures")) / parsed.head
  require(!capture.exists(), "Rejected filter capture must be new")
  IO.copyDirectory(audit, capture / "body-audit")
  IO.copyDirectory(target.value / "test-reports", capture / "test-reports")
  streams.value.log.info("PLUGIN_REJECTED_FILTER_OK case=" + parsed.head + " bodies=0 reported=1 errors=1 acquired=0 released=0")
}
'''


SBT2_DIGEST_CONTROL = '''
val verifyDistinctStockDigests = taskKey[Unit]("Verify the stock incremental fixture precondition")
verifyDistinctStockDigests := Def.uncached {
  val names = Vector("SuiteA", "SuiteB", "SuiteC", "SuiteD", "SuiteE").map("izumi.fixtures.host." + _)
  val digests = (Test / definedTestDigests).value
  require(names.forall(digests.contains), "A stock suite digest is missing")
  val distinct = names.map(name => digests(name).toString).distinct.size
  require(distinct == names.size, "Stock suite digests are not distinct")
  val history = target.value / "history-digest-baseline"
  IO.createDirectory(history)
  (names :+ "izumi.fixtures.host.ForeignSuite").foreach { name => IO.write(history / name,digests(name).toString) }
  streams.value.log.info("PLUGIN_STOCK_DIGESTS_OK suites=" + names.size + " distinct=" + distinct)
}
val verifyScannedImplementationDigests = taskKey[Unit]("Confirm the edited plugin implementation is outside the stock suite digest closure")
verifyScannedImplementationDigests := Def.uncached {
  val history = target.value / "history-digest-baseline"
  val digests = (Test / definedTestDigests).value
  val baselines = (history * "*").get()
  require(baselines.map(_.name).toSet == (Test / definedTests).value.map(_.name).toSet,"Stock history baseline catalogue differs")
  baselines.foreach { baseline => require(digests(baseline.name).toString == IO.read(baseline),"Scanned implementation changed a stock suite digest: " + baseline.name) }
  streams.value.log.info("PLUGIN_SCANNED_DIGESTS_UNCHANGED_OK")
}
val verifyChangedSuiteDigest = taskKey[Unit]("Confirm the edited suite invalidates its distinct stock digest")
verifyChangedSuiteDigest := Def.uncached {
  val history = target.value / "history-digest-baseline"
  val digests = (Test / definedTestDigests).value
  require(digests("izumi.fixtures.host.SuiteC").toString != IO.read(history / "izumi.fixtures.host.SuiteC"),"Suite class edit did not change its stock digest")
  require(digests("izumi.fixtures.host.ForeignSuite").toString == IO.read(history / "izumi.fixtures.host.ForeignSuite"),"Suite class edit changed the foreign stock digest")
  streams.value.log.info("PLUGIN_CHANGED_SUITE_DIGEST_OK")
}
'''


def commands(sbt_version):
    quick = "test"
    full = "testFull"
    sequence, expected = ["clean"], []
    if sbt_version == "2.0.9":
        sequence.extend(["verifyDistinctStockDigests", "show Test / definedTestDigests"])
    selected_id = dict(target="pluginConsumer/test", suite="izumi.fixtures.host.SuiteC", path=["equal display name", "should", "first"], variant=None)
    selected_json = json.dumps(selected_id, separators=(",", ":")).replace(" ", "\\u0020")
    selected_options = "--test-id " + json.dumps(selected_json) + " --memoization disabled"
    axis = dict(axis="repo", value="dummy")
    axis_json = json.dumps(json.dumps(axis, separators=(",", ":")))
    selected_options += " --axis " + axis_json + " --axis-filter " + axis_json
    implementation = "one"

    def case(name, command, labels, acquisitions, revision):
        sequence.extend(["prepareFixture " + name, command])
        if labels:
            sequence.append(f"verifyFixture {name} {acquisitions} {labels}")
        else:
            sequence.append("verifyNoRun " + name)
        expected.append(dict(case=name, kind="full", labels=labels.split(), acquisitions=acquisitions, revision=revision, repo="prod", implementation=implementation))

    def inspect(name, operation, options, count, target):
        fork_sentinel = target == "pluginConsumer/inspection"
        sequence.append("prepareFixture " + name)
        if fork_sentinel:
            sequence.append("verifyInspectionParent " + name)
        sequence.extend([operation + (" " + options if options else ""), "verifyNoRun " + name])
        ids = ([dict(target=target, suite="izumi.fixtures.host.ForkSentinelSuite", path=["must not execute"], variant=None)] if fork_sentinel else
               [selected_id] if options else [dict(target=target, suite="izumi.fixtures.host." + suite, path=["equal display name", "should", leaf], variant=None) for suite in ALL_SUITES.split() for leaf in ["first", "second", "third"]])
        expected.append(dict(case=name, kind="inspection", labels=[], acquisitions=0, revision="alpha", operation="planned" if "distagePlan" in operation else "resolved", testCount=count, target=target, selected=bool(options), expectedIds=ids, forkSentinel=fork_sentinel, expectedAxis=axis if options else None))

    def selected(name, command, revision):
        sequence.extend(["prepareFixture " + name, command + " -- " + selected_options, "verifySelectedFixture " + name])
        expected.append(dict(case=name, kind="selected", labels=["SuiteC"], acquisitions=1, revision=revision, repo="dummy", implementation=implementation))

    def rejected_filter(name, revision):
        sequence.extend(["prepareFixture " + name, "verifyRejectedAxisFilter " + name])
        expected.append(dict(case=name, kind="rejected-filter", labels=[], acquisitions=0, revision=revision))

    inspect("list", "Test / distageList", "", 15, "pluginConsumer/test")
    inspect("plan", "Test / distagePlan", "", 15, "pluginConsumer/test")
    inspect("selected-plan", "Test / distagePlan", selected_options, 1, "pluginConsumer/test")
    selected("individual", "testOnly *SuiteC", "alpha")
    case("after-individual", quick + " *SuiteC", "SuiteC", 1, "alpha")
    rejected_filter("rejected-filter", "alpha")
    case("after-rejected-filter", quick + " *SuiteC", "SuiteC", 1, "alpha")
    case("memoization-disabled", "testOnly *SuiteC -- --memoization disabled", "SuiteC", 3, "alpha")

    case("full", full, ALL_SUITES + " ForeignSuite", 1, "alpha")
    case("conservative-repeat", quick, ALL_SUITES, 1, "alpha")
    selected("quick-individual", "testQuick *SuiteC", "alpha")
    case("quick-after-individual", "testQuick *SuiteC", "SuiteC", 1, "alpha")
    case("quick-memoization-disabled", "testQuick *SuiteC -- --memoization disabled", "SuiteC", 3, "alpha")
    case("quick-activation", "testQuick *SuiteC -- --axis " + axis_json, "SuiteC", 1, "alpha")
    expected[-1]["repo"] = "dummy"
    case("quick-after-partial", "testQuick", ALL_SUITES, 1, "alpha")
    sequence.append("changeExternalInput beta")
    case("changed-input", quick, ALL_SUITES, 1, "beta")
    sequence.append("changeExternalInput delta")
    case("quick-changed-input", "testQuick", ALL_SUITES, 1, "delta")
    sequence.extend(["changeExternalInput beta", "changeScannedImplementation", "verifyScannedImplementationDigests"])
    implementation = "two"
    case("quick-scanned-implementation", "testQuick", ALL_SUITES, 1, "beta")
    case("test-scanned-implementation", quick, ALL_SUITES, 1, "beta")
    sequence.extend(["changeSuiteClass", "verifyChangedSuiteDigest"])
    case("quick-changed-suite", "testQuick", ALL_SUITES, 1, "beta")
    case("test-changed-suite", quick, ALL_SUITES, 1, "beta")
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
    inspect("fork-list", "Test / distageList", "", 15, "pluginConsumer/test")
    inspect("fork-selected-plan", "Test / distagePlan", selected_options, 1, "pluginConsumer/test")
    selected("fork-individual", "testOnly *SuiteC", "beta")
    case("fork-after-individual", quick + " *SuiteC", "SuiteC", 1, "beta")
    rejected_filter("fork-rejected-filter", "beta")
    case("fork-after-rejected-filter", quick + " *SuiteC", "SuiteC", 1, "beta")
    case("fork-full", full, ALL_SUITES + " ForeignSuite", 1, "beta")
    case("fork-selected", quick + " *SuiteC *SuiteD", "SuiteC SuiteD", 1, "beta")
    sequence.append("changeExternalInput gamma")
    case("fork-changed-input", quick, ALL_SUITES, 1, "gamma")
    case("custom-selected", "Integration / testOnly *SuiteC *SuiteD", "SuiteC SuiteD", 1, "gamma")
    case("custom-repeat", "Integration / testQuick *SuiteD", "SuiteD", 1, "gamma")
    inspect("custom-list", "Integration / distageList", "", 15, "pluginConsumer/it")
    inspect("custom-plan", "Integration / distagePlan", "", 15, "pluginConsumer/it")
    inspect("sentinel-list", "InspectionOnly / distageList", "", 1, "pluginConsumer/inspection")
    inspect("sentinel-plan", "InspectionOnly / distagePlan", "", 1, "pluginConsumer/inspection")
    sequence.extend(["show Test / testFrameworks", "show Test / dependencyClasspath", "show Test / fullClasspath",
                     "show Integration / testFrameworks", "show Integration / distageTargetId"])
    return sequence, expected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-version", required=True)
    parser.add_argument("--sbt-version", nargs="+", required=True, choices=["2.0.9"])
    parser.add_argument("--scala-version", nargs="+", required=True, choices=["3.9.0", "2.13.18"])
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
                    text = text[:start] + text[end:] + CONTROLS + REJECTED_FILTER_CONTROL + (SBT2_DIGEST_CONTROL if sbt_version == "2.0.9" else "")
                elif source.name == "FixturePlugin.scala":
                    before = 'new SharedResource(repo + "-" + UUID.randomUUID().toString, Paths.get(sys.props("izumi.fixture.audit-root")))'
                    assert text.count(before) == 1
                    text = text.replace(before, 'new SharedResource(new String(Files.readAllBytes(Paths.get(sys.props("izumi.fixture.external-input"))), StandardCharsets.UTF_8).trim + "-" + repo + "-" + implementationRevision + "-" + UUID.randomUUID().toString, Paths.get(sys.props("izumi.fixture.audit-root")))')
                    text = text.replace('  private def resource(repo: String)', '  private def implementationRevision: String = "one"\n\n  private def resource(repo: String)')
                target.write_text(text)
            (build / "project").mkdir()
            (build / "project/build.properties").write_text("sbt.version=" + sbt_version + "\n")
            (build / "project/plugins.sbt").write_text('addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + arguments.artifact_version + '")\n')
            sentinel_source = build / "src/inspection/scala/izumi/fixtures/host/ForkSentinelSuite.scala"
            sentinel_source.parent.mkdir(parents=True)
            sentinel_source.write_text(FORK_SENTINEL_SOURCE)
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
                marker = (f"PLUGIN_REJECTED_FILTER_OK case={name} bodies=0 reported=1 errors=1 acquired=0 released=0" if row["kind"] == "rejected-filter" else
                          f"PLUGIN_SELECTED_OK case={name} bodies=1 reported=1 acquired=1 released=1" if row["kind"] == "selected" else
                          f"TARGET_BOOTSTRAP_HOST_OK case={name} suites={len(labels)} bodies={len(labels) * 3} reported={len(labels) * 3} acquired={row['acquisitions']} released={row['acquisitions']}"
                          if labels else "PLUGIN_STOCK_NOOP_OK case=" + name)
                if raw.count(marker + "\n") != 1:
                    failures.append("Missing unique case marker: " + marker)
                if row["acquisitions"]:
                    records = list((lane / "cases" / name / "body-audit").glob("*.acquire"))
                    prefix = row["revision"] + "-" + row["repo"] + "-" + row["implementation"] + "-"
                    if len(records) != row["acquisitions"] or any(not record.read_text().startswith(prefix) for record in records):
                        failures.append("DI acquisition did not consume current external input and scanned implementation: " + name)
                if row["kind"] == "inspection":
                    start = raw.find("TARGET_BOOTSTRAP_PREPARED case=" + name + "\n")
                    end = raw.find("PLUGIN_STOCK_NOOP_OK case=" + name + "\n", start)
                    if start < 0 or end < 0:
                        failures.append("Inspection case did not complete: " + name)
                        continue
                    frames = re.findall(r'DISTAGE_INSPECTION (\{[^\n]+\})', raw[start:end])
                    if len(frames) != 1:
                        failures.append("Inspection must emit exactly one frame: " + name)
                        continue
                    frame = json.loads(frames[0])
                    message = frame["message"]
                    selection = message["selection"] if message["kind"] == "resolved" else message["plan"]["selection"]
                    ids = [test["id"] for test in selection["tests"]]
                    if frame["schemaVersion"] != 4 or message["kind"] != row["operation"] or len(ids) != row["testCount"] or any(test["target"] != row["target"] for test in ids):
                        failures.append("Inspection operation/count/target differs: " + name)
                    if sorted(json.dumps(test, sort_keys=True) for test in ids) != sorted(json.dumps(test, sort_keys=True) for test in row["expectedIds"]):
                        failures.append("Inspection exact identity set differs: " + name)
                    if row["selected"] and any(test["settings"]["memoization"] for test in selection["tests"]):
                        failures.append("Inspection selected ID or effective memoization differs: " + name)
                    if row["selected"]:
                        axis = row["expectedAxis"]
                        overrides = selection["request"]["overrides"]
                        if overrides["axes"] != [axis] or overrides["axisFilters"] != [axis] or any(axis not in test["settings"]["axes"] for test in selection["tests"]):
                            failures.append("Inspection activation override/filter differs: " + name)
                    if message["kind"] == "planned" and message["plan"]["inspection"]["failures"]:
                        failures.append("Positive inspection contains planning failures: " + name)
                    if row["forkSentinel"]:
                        parent = re.findall(r'PLUGIN_INSPECTION_PARENT_OK pid=(\d+) sentinel=absent case=' + re.escape(name), raw[start:end])
                        child = re.findall(r'PLUGIN_INSPECTION_FORK_SENTINEL_OK pid=(\d+) value=owned-value', raw[start:end])
                        if len(parent) != 1 or len(child) != 1 or parent == child or "INSPECTION_EXECUTED_BODY" in raw[start:end]:
                            failures.append("Inspection did not run in its configured child JVM: " + name)
            for diagnostic in ["RejectedExecutionException:", "NoClassDefFoundError:", "ClassNotFoundException:", "Exception in thread "]:
                if diagnostic in raw:
                    failures.append("Unexpected runtime diagnostic: " + diagnostic)
            if "DISTAGE_CACHE_DECISION suite=izumi.fixtures.host.SuiteC decision=rerun reason=untracked-input-closure" not in raw:
                failures.append("Explicit conservative cache decision missing")
            history_markers = ["PLUGIN_HISTORY_EDIT_OK kind=scanned-implementation revision=two", "PLUGIN_SCANNED_DIGESTS_UNCHANGED_OK", "PLUGIN_HISTORY_EDIT_OK kind=suite-class revision=4", "PLUGIN_CHANGED_SUITE_DIGEST_OK"]
            for marker in history_markers:
                if raw.count(marker + "\n") != 1: failures.append("Missing unique history evidence: " + marker)
            for row in rows:
                assert hashlib.sha256(Path(row["path"]).read_bytes()).hexdigest() == row["sha256"]
            digests = re.findall(r'(izumi\.fixtures\.host\.Suite[A-E]) -> (sha256-[a-f0-9]+/[0-9]+)', raw)
            if sbt_version == "2.0.9" and (len(digests) != 5 or len(set(d for _, d in digests)) != 5 or raw.count("PLUGIN_STOCK_DIGESTS_OK suites=5 distinct=5\n") != 1):
                failures.append("Distinct stock suite digest precondition was not established before execution")
            if sbt_version == "2.0.9" and "PLUGIN_STOCK_DIGESTS_OK suites=5 distinct=5\n" in raw and "TARGET_BOOTSTRAP_PREPARED case=list\n" in raw:
                if raw.index("PLUGIN_STOCK_DIGESTS_OK suites=5 distinct=5\n") > raw.index("TARGET_BOOTSTRAP_PREPARED case=list\n"):
                    failures.append("Stock suite digest precondition ran after a fixture case")
            outcome = dict(sbt=sbt_version, scala=scala_version, actualExit=code, expectedCases=len(expected), validationFailures=failures,
                           stockDigestPairs=digests, distinctStockSuiteDigests=len(digests) == 5 and len(set(d for _, d in digests)) == 5, historyMarkers=history_markers)
            (lane / "completion.json").write_text(json.dumps(outcome, indent=2) + "\n")
            outcomes.append(outcome)
            print(json.dumps(outcome), flush=True)
            if code or failures:
                (evidence / "completion.json").write_text(json.dumps(dict(exit=1, outcomes=outcomes), indent=2) + "\n")
                raise SystemExit(1)
    (evidence / "completion.json").write_text(json.dumps(dict(exit=0, outcomes=outcomes), indent=2) + "\n")


if __name__ == "__main__":
    main()
