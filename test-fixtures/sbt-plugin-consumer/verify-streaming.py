from pathlib import Path
import json
import subprocess
import time
from xml.etree import ElementTree

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import write_sbt_project, wait_process, consumer_parser

BODY_TIMEOUT_SECONDS = 30
COMMAND_TIMEOUT_SECONDS = 180
SOURCE = r'''package fixture
import izumi.distage.testkit.runner.spec.AnyWordSpec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
final class StreamingSuite extends AnyWordSpec {
  private final val BodyTimeoutSeconds = 30L
  private def audit = Paths.get(sys.props("fixture.audit-root"))
  private def entered(name: String): Unit = {
    val _ = Files.write(audit.resolve(name), java.lang.ProcessHandle.current().pid().toString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW)
  }
  "stream" should {
    "first" in {
      entered("first.body")
    }
    "held" in {
      entered("held.body")
      val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(BodyTimeoutSeconds)
      while (!Files.exists(audit.resolve("allow")) && System.nanoTime() < deadline) Thread.sleep(5L)
      require(Files.exists(audit.resolve("allow")), "STREAMING_BODY_RELEASE_MISSING")
    }
  }
}
'''
BUILD = r'''
lazy val streaming = project.in(file(".")).enablePlugins(izumi.distage.sbt.DistageTestkitPlugin)
scalaVersion := @SCALA@
libraryDependencies += "io.7mind.izumi" %% "distage-test-runner" % @VERSION@ % Test
scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17") }
Test / target := file(@TARGET@)
target := baseDirectory.value / "target"
Test / fork := @FORK@
Test / javaOptions += @AUDIT_OPTION@
lazy val recordStreamingHost = taskKey[Unit]("Record the SBT execution process")
recordStreamingHost := Def.uncached { IO.write(file(@HOST_RECEIPT@), java.lang.ProcessHandle.current().pid().toString) }
'''


def frames(directory, complete):
    paths = sorted(directory.glob('*.jsonl'))
    result = []
    for path in paths:
        payload = path.read_text(encoding='utf-8')
        if complete and not payload.endswith('\n'):
            raise ValueError('Stream ended inside a frame')
        for line in payload.splitlines(keepends=True):
            if line.endswith('\n'):
                envelope = json.loads(line)
                if envelope['schemaVersion'] != 4:
                    raise ValueError('Unexpected event schema')
                result.append(envelope['message'])
    return paths, result


def identity(test):
    return test['target'], test['suite'], tuple(test['path']), test['variant']


def main():
    parser = consumer_parser()
    parser.add_argument('--scala-version', choices=['3.9.0', '2.13.18'], required=True)
    parser.add_argument('--fork', choices=['true', 'false'], required=True)
    args = parser.parse_args()
    out = args.evidence_dir.resolve()
    out.mkdir()
    build = out / 'build'
    audit = out / 'audit'
    target = build / 'target/test'
    (build / 'src/test/scala').mkdir(parents=True)
    (build / 'src/test/scala/StreamingSuite.scala').write_text(SOURCE)
    definition = BUILD.replace('@SCALA@', json.dumps(args.scala_version)).replace('@VERSION@', json.dumps(args.artifact_version)).replace('@TARGET@', json.dumps(str(target))).replace('@FORK@', args.fork).replace('@AUDIT_OPTION@', json.dumps('-Dfixture.audit-root=' + str(audit))).replace('@HOST_RECEIPT@', json.dumps(str(audit / 'host.pid')))
    write_sbt_project(build, definition, '2.0.9', 'addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % "' + args.artifact_version + '")\n')
    audit.mkdir()
    argv = ['direnv', 'exec', str(args.repo_root.resolve()), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'streaming', '-Dfixture.audit-root=' + str(audit), 'recordStreamingHost', 'show Test / javaOptions', 'testOnly fixture.StreamingSuite']
    (out / 'command.json').write_text(json.dumps(dict(argv=argv, cwd=str(build)), indent=2) + '\n')
    failures = []
    held_frames = []
    with (out / 'run.log').open('w') as log:
        process = subprocess.Popen(argv, cwd=build, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic() + COMMAND_TIMEOUT_SECONDS
            while not ((audit / 'first.body').exists() and (audit / 'held.body').exists()):
                if process.poll() is not None or time.monotonic() >= deadline:
                    failures.append('Bodies did not enter: inspect run.log')
                    break
                time.sleep(0.05)
            if not failures:
                deadline = time.monotonic() + BODY_TIMEOUT_SECONDS / 3
                while time.monotonic() < deadline:
                    _, held_frames = frames(target / 'distage-events', complete=False)
                    starts = [frame for frame in held_frames if frame.get('kind') == 'event' and frame['event']['kind'] == 'testStarted']
                    finishes = [frame for frame in held_frames if frame.get('kind') == 'event' and frame['event']['kind'] == 'testCompleted']
                    if len(starts) == 2 and len(finishes) == 1:
                        break
                    time.sleep(0.05)
                else:
                    failures.append('STREAM_NOT_VISIBLE_BEFORE_GROUP_COMPLETE')
                if process.poll() is not None:
                    failures.append('Command completed while the second body was held')
                if any(frame['kind'] == 'completed' or frame.get('event', {}).get('kind') == 'finished' for frame in held_frames):
                    failures.append('Stream completed while the second body was held')
                if finishes and finishes[0]['event']['result']['id']['path'] != ['stream', 'should', 'first']:
                    failures.append('Held-body completion was reported before release')
        finally:
            (audit / 'allow').write_text('release\n')
            actual = wait_process(process, COMMAND_TIMEOUT_SECONDS, 10)
    if actual != 0:
        failures.append('SBT failed: inspect run.log')
    host_pid = int((audit / 'host.pid').read_text())
    body_pids = {int(path.read_text()) for path in audit.glob('*.body')}
    if len(body_pids) != 1 or ((host_pid in body_pids) != (args.fork == 'false')):
        failures.append('Body process ownership differs from fork settings')
    paths, final_frames = frames(target / 'distage-events', complete=True)
    events = [frame for frame in final_frames if frame['kind'] == 'event']
    starts = [frame['event']['test'] for frame in events if frame['event']['kind'] == 'testStarted']
    results = [frame['event']['result'] for frame in events if frame['event']['kind'] == 'testCompleted']
    terminals = [frame['outcome'] for frame in final_frames if frame['kind'] == 'completed']
    finished = [frame['event']['outcome'] for frame in events if frame['event']['kind'] == 'finished']
    if len(paths) != 1 or len(final_frames) != 7 or len(events) != 6:
        failures.append('Expected one complete seven-frame run stream')
    if len(starts) != 2 or len(results) != 2 or sorted(test['path'] for test in starts) != [['stream', 'should', 'first'], ['stream', 'should', 'held']]:
        failures.append('Start identities differ from physical bodies')
    if {identity(test) for test in starts} != {identity(result['id']) for result in results} or any(result['status'] != 'succeeded' or result['failure'] is not None for result in results):
        failures.append('Terminal test identities/statuses differ from starts')
    if len(terminals) != 1 or finished != terminals or terminals[0]['results'] != results or terminals[0]['failures'] or terminals[0]['cancelled']:
        failures.append('Run outcomes differ from streamed test outcomes')
    if events and ([int(frame['sequence']) for frame in events] != list(range(int(events[0]['sequence']), int(events[0]['sequence']) + len(events))) or events[0]['event']['kind'] != 'started' or events[-1]['event']['kind'] != 'finished'):
        failures.append('Event sequence is incomplete or out of order')
    if terminals and ({frame['event']['run'] for frame in events} != {terminals[0]['run']} or paths[0].stem != terminals[0]['run']):
        failures.append('Frame/file run ownership differs')
    reports = [ElementTree.parse(path) for path in (target / 'test-reports').glob('*.xml')]
    cases = [node for report in reports for node in report.findall('.//testcase')]
    if sorted((node.attrib['classname'], node.attrib['name']) for node in cases) != [('fixture.StreamingSuite', 'stream should first'), ('fixture.StreamingSuite', 'stream should held')] or any(node.find('failure') is not None or node.find('error') is not None for node in cases):
        failures.append('JUnit outcomes differ from streamed test outcomes')
    result = dict(exit=int(bool(failures)), actualExit=actual, scala=args.scala_version, fork=args.fork, hostPid=host_pid, bodyPids=sorted(body_pids), heldFrames=held_frames, finalFrames=final_frames, xmlCases=len(cases), failures=failures)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)
    raise SystemExit(result['exit'])


if __name__ == '__main__':
    main()
