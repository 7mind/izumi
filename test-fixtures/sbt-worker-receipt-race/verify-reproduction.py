#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from xml.etree import ElementTree


HELD_EVENT_MILLIS = 1200
LANE_TIMEOUT_SECONDS = 300


SOURCE = '''package fixture
import sbt.testing.{Event, EventHandler, Fingerprint, Framework, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, Task, TaskDef, TestSelector}
abstract class ErrorSpec
final class ErrorSuite extends ErrorSpec
final class ErrorFramework extends Framework {
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[ErrorSpec].getName
    override def requireNoArgConstructor(): Boolean = true
  }
  override def name(): String = "generic-error-control"
  override def fingerprints(): Array[Fingerprint] = Array(fingerprint)
  override def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner = new Runner {
    override def args(): Array[String] = arguments
    override def remoteArgs(): Array[String] = remoteArguments
    override def done(): String = "generic control complete"
    override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.map { definition => new Task {
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = Array.empty[String]
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        handler.handle(new Event {
          override def fullyQualifiedName(): String = definition.fullyQualifiedName()
          override def fingerprint(): Fingerprint = definition.fingerprint()
          override def selector(): Selector = new TestSelector("intentional error")
          override def status(): Status = Status.Error
          override def throwable(): OptionalThrowable = new OptionalThrowable(new IllegalStateException("INTENTIONAL_GENERIC_ERROR"))
          override def duration(): Long = 0L
        })
        Array.empty[Task]
      }
    } }
  }
}
'''

BUILD = '''import java.util.concurrent.{CountDownLatch, TimeUnit}
scalaVersion := "3.9.0"
libraryDependencies += "org.scala-sbt" % "test-interface" % "1.0" % Test
scalacOptions ++= Seq("-release:17", "-Ybackend-parallelism", "1")
Test / fork := true
Test / testFrameworks := Seq(new TestFramework("fixture.ErrorFramework"))
val eventReceived = settingKey[CountDownLatch]("Owned fixture listener completion")
eventReceived := new CountDownLatch(1)
Test / testListeners += {
  val received = eventReceived.value
  val holdMillis = sys.props("fixture.hold-millis").toLong
  new TestsListener {
    override def doInit(): Unit = ()
    override def startGroup(name: String): Unit = ()
    override def testEvent(event: TestEvent): Unit = {
      println("GENERIC_LISTENER_ENTER result=" + event.result)
      Thread.sleep(holdMillis)
      println("GENERIC_LISTENER_EXIT result=" + event.result)
    }
    override def endGroup(name: String, cause: Throwable): Unit = received.countDown()
    override def endGroup(name: String, result: TestResult): Unit = {
      println("GENERIC_LISTENER_GROUP result=" + result)
      received.countDown()
    }
    override def doComplete(result: TestResult): Unit = println("GENERIC_LISTENER_COMPLETE result=" + result)
  }
}
val awaitFixtureEvent = taskKey[Unit]("Await the owned event receipt after testOnly")
val eventTimeoutSeconds = 10L
awaitFixtureEvent := Def.uncached {
  require(eventReceived.value.await(eventTimeoutSeconds, TimeUnit.SECONDS), "Generic event did not complete")
  streams.value.log.info("GENERIC_LISTENER_AWAIT_OK")
}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path, required=True)
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    evidence = arguments.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    outcomes = []
    driver_bytes = Path(__file__).read_bytes()
    (evidence / "verify-reproduction.py").write_bytes(driver_bytes)
    for sdk in ["2.0.9", "1.13.0"]:
        for name, hold in [("fast", 0), ("held", HELD_EVENT_MILLIS)]:
            lane = evidence / ("sbt" + sdk + "-" + name)
            build = lane / "build"
            (build / "project").mkdir(parents=True)
            (build / "src/test/scala").mkdir(parents=True)
            (build / "src/test/scala/ErrorFramework.scala").write_text(SOURCE)
            build_text = BUILD if sdk == "2.0.9" else BUILD.replace("Def.uncached {", "{")
            (build / "build.sbt").write_text(build_text)
            (build / "project/build.properties").write_text("sbt.version=" + sdk + "\n")
            commands = ["clean", "show Test / dependencyClasspath", "show Test / fullClasspath", "testOnly *ErrorSuite", "awaitFixtureEvent"]
            if sdk == "2.0.9":
                commands.insert(0, 'set Global / localCacheDirectory := file("' + str(lane / "local-cache") + '")')
            invocation = 'task_sbt_version="$1"; shift; exec sbt --server --sbt-version "$task_sbt_version" -java-home "$JDK21" -batch -J-Xmx6G "$@"'
            argv = ["direnv", "exec", str(root), "sh", "-c", invocation, "generic-worker-reproduction", sdk,
                    "-Dfixture.hold-millis=" + str(hold), *commands]
            inputs = [dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest()) for path in build.rglob("*") if path.is_file()]
            (lane / "commands.json").write_text(json.dumps(dict(argv=argv, cwd=str(build), inputs=inputs), indent=2) + "\n")
            print("WORKER_RECEIPT_LANE_START " + str(lane), flush=True)
            with (lane / "run.log").open("x") as log:
                result = subprocess.run(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, timeout=LANE_TIMEOUT_SECONDS)
                log.write("\nEXIT " + str(result.returncode) + "\n")
            raw = (lane / "run.log").read_text()
            reports = []
            for path in (build / "target").glob("**/test-reports/*.xml"):
                xml = ElementTree.parse(path)
                reports.append(dict(path=str(path), suite=xml.getroot().attrib,
                                    cases=[node.attrib for node in xml.findall(".//testcase")],
                                    errors=[node.attrib for node in xml.findall(".//error")]))
            for row in inputs:
                assert hashlib.sha256(Path(row["path"]).read_bytes()).hexdigest() == row["sha256"]
            assert Path(__file__).read_bytes() == driver_bytes
            expected_exit = 0 if sdk == "2.0.9" and name == "held" else 1
            expected_result = "Passed" if expected_exit == 0 else "Error"
            valid = (result.returncode == expected_exit and len(reports) == 1 and reports[0]["suite"]["errors"] == "1"
                     and reports[0]["suite"]["tests"] == "1" and reports[0]["suite"]["failures"] == "0"
                     and reports[0]["suite"]["skipped"] == "0" and len(reports[0]["cases"]) == len(reports[0]["errors"]) == 1
                     and reports[0]["cases"][0]["classname"] == "fixture.ErrorSuite"
                     and "INTENTIONAL_GENERIC_ERROR" in reports[0]["errors"][0]["message"]
                     and raw.count("GENERIC_LISTENER_GROUP result=Error\n") == 1
                     and raw.count("GENERIC_LISTENER_COMPLETE result=" + expected_result + "\n") == 1)
            if expected_exit == 0:
                valid = valid and raw.count("GENERIC_LISTENER_AWAIT_OK\n") == 1 and "No tests to run for Test / testSelected" in raw
            outcome = dict(sbt=sdk, case=name, actualExit=result.returncode, expectedExit=expected_exit, valid=valid, reports=reports,
                           listenerMarkers=[line for line in raw.splitlines() if "GENERIC_LISTENER_" in line],
                           emptySummary="No tests to run for Test / testSelected" in raw)
            (lane / "completion.json").write_text(json.dumps(outcome, indent=2) + "\n")
            outcomes.append(outcome)
            print(json.dumps({key: value for key, value in outcome.items() if key not in ["reports", "listenerMarkers"]}), flush=True)
    valid = all(outcome["valid"] for outcome in outcomes)
    record = dict(exit=0 if valid else 1, workerReceiptRaceReproduced=valid, outcomes=outcomes,
                  scope="Public generic Framework and TestsListener; no distage dependencies/plugin or private SDK changes. Driver0 requires the reproduced SBT2 defect, not product correctness.")
    (evidence / "completion.json").write_text(json.dumps(record, indent=2) + "\n")
    raise SystemExit(0 if valid else 1)


if __name__ == "__main__":
    main()
