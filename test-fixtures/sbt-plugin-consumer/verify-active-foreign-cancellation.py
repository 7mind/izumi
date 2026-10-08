#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import traceback
from xml.etree import ElementTree


import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import write_sbt_project, load_module as module

FOREIGN_GATE = r'''
        private final val GateTimeoutSeconds = 30L
        private final val PollMillis = 5L
        private def awaitFile(path: java.nio.file.Path): Unit = {
          val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(GateTimeoutSeconds)
          while (!java.nio.file.Files.isRegularFile(path) && System.nanoTime() < deadline) {
            try Thread.sleep(PollMillis)
            catch { case _: InterruptedException => Audit.write(java.util.UUID.randomUUID().toString + ".foreign-interrupt", java.lang.ProcessHandle.current().pid().toString) }
          }
          require(java.nio.file.Files.isRegularFile(path), "ACTIVE_FOREIGN_GATE_TIMEOUT")
        }
'''





def replace_once(text, original, replacement):
    assert text.count(original) == 1, original
    return text.replace(original, replacement)


def verify(capture, row, runs, resources):
    audit = capture / 'audit'
    bodies = [p.read_text().split('\t') for p in audit.glob('*.body')]
    expected = {(f'fixture.Suite{s}', str(i)) for s in 'ABCDE' for i in range(1, 4)}
    assert len(bodies) == 15 and {(b[0], b[1]) for b in bodies} == expected, 'ACTIVE_FOREIGN_OWNED_BODIES_CHANGED'
    acquired = [p.read_text() for p in audit.glob('*.acquire')]
    released = [p.read_text() for p in audit.glob('*.release')]
    assert len(acquired) == 1 and acquired == released and acquired[0] not in resources
    resources.update(acquired)
    assert all(b[2] == acquired[0] for b in bodies)
    host = (audit / 'host.pid').read_text()
    targets = {b[3] for b in bodies}
    assert len(targets) == 1 and ((host in targets) == (not row['fork']))
    streams = list((capture / 'events').glob('*.jsonl'))
    assert len(streams) == 1
    frames = [json.loads(line)['message'] for line in streams[0].read_text().splitlines()]
    assert frames[-1]['kind'] == 'completed'
    outcome = frames[-1]['outcome']
    assert outcome['run'] not in runs and not outcome['cancelled'] and not outcome['failures']
    runs.add(outcome['run'])
    assert len(outcome['results']) == 15 and all(r['status'] == 'succeeded' for r in outcome['results'])
    foreign = [p.read_text().split('\t') for p in audit.glob('*.foreign-body')]
    assert len(foreign) == 3 and {(b[0], b[1]) for b in foreign} == {('fixture.ForeignSuite', str(i)) for i in range(1, 4)}
    assert all(b[2] in targets for b in foreign)
    events = [tuple(p.read_text().split('\t')) for p in audit.glob('*.foreign-event')]
    expected_events = {('fixture.ForeignSuite', 'foreign-' + str(i), 'Success', str(i * 10), 'false', 'fixture.ForeignMarker', 'false', 'true') for i in range(1, 4)}
    assert len(events) == 3 and set(events) == expected_events, 'ACTIVE_FOREIGN_EVENT_PAYLOAD_CHANGED'
    nodes = [n for p in (capture / 'test-reports').glob('*.xml') for n in ElementTree.parse(p).findall('.//testcase')]
    expected_xml = {(suite, 'same display name should ' + leaf) for suite, _ in expected for leaf in ['first', 'second', 'third']}
    expected_xml.update(('fixture.ForeignSuite', 'foreign-' + str(i)) for i in range(1, 4))
    assert len(nodes) == 18 and {(n.get('classname'), n.get('name')) for n in nodes} == expected_xml, 'ACTIVE_FOREIGN_XML_CHANGED'
    assert all(n.find('failure') is None and n.find('error') is None and n.find('skipped') is None for n in nodes)
    assert not (audit / 'foreign.late').exists(), 'ACTIVE_FOREIGN_EVENT_AFTER_COMMAND'
    if row['mode'] == 'cancel':
        assert (audit / 'foreign.observed-held').read_text() == 'held'
        assert 'error' in row['response'] or row['response']['result']['exitCode'] != 0
        assert row['cancelResponse']['result']['status'] == 'Task cancelled'
    else:
        assert 'error' not in row['response'] and row['response']['result']['exitCode'] == 0
    return dict(**row, bodies=15, foreignBodies=3, foreignEvents=3, xmlCases=18, resource=acquired[0], run=outcome['run'], hostPid=host, bodyPids=sorted(targets))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo-root', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--artifact-version', required=True)
    parser.add_argument('--scala-version', choices=['3.9.0', '2.13.18'], required=True)
    args = parser.parse_args()
    root = args.repo_root.resolve()
    out = args.evidence_dir.resolve()
    out.mkdir()
    api_path = root / 'test-fixtures/sbt-plugin-consumer/verify-client-cancellation.py'
    fixture_path = root / 'test-fixtures/sbt-plugin-consumer/verify-task-cancellation.py'
    mixed_path = root / 'test-fixtures/sbt-plugin-consumer/verify-mixed-client-cancellation.py'
    api = module('client_api', api_path)
    fixture = module('fixture', fixture_path)
    mixed = module('mixed', mixed_path)
    build = out / 'build'
    source = build / 'src/test/scala'
    source.mkdir(parents=True)
    owned = replace_once(fixture.SOURCE, 'if (Audit.mode.startsWith("cancel"))', 'if (false)')
    plugin = replace_once(fixture.PLUGIN, 'if (Audit.mode.startsWith("cancel"))', 'if (false)')
    (source / 'Suites.scala').write_text(owned.replace('@AUDIT@', json.dumps(str(build / 'audit'))) + '\n' + '\n'.join(f'final class {s} extends CancellationSuite' for s in fixture.SUITES) + '\n')
    (source / 'Plugin.scala').write_text(plugin)
    foreign = replace_once(mixed.FOREIGN, '      override def execute(handler:', FOREIGN_GATE + '      override def execute(handler:')
    foreign = replace_once(foreign, '          val event = new Event {', '''          if (index == 2 && Audit.mode == "cancel") {
            Audit.write("foreign.entered", java.lang.ProcessHandle.current().pid().toString)
            awaitFile(Audit.directory.resolve("allow-foreign-release"))
            Audit.write("foreign.released", java.lang.ProcessHandle.current().pid().toString)
          }
          val event = new Event {''')
    (source / 'Foreign.scala').write_text(foreign)
    settings = fixture.SETTINGS[:fixture.SETTINGS.index('val proxyFramework')] + fixture.SETTINGS[fixture.SETTINGS.index('lazy val prepareCancellation'):]
    settings = settings.replace('@SCALA@', json.dumps(args.scala_version)).replace('@VERSION@', json.dumps(args.artifact_version)).replace('@CAPTURES@', json.dumps(str(out / 'cases')))
    listener = replace_once(mixed.LISTENER, 'new TestFramework("fixture.ForeignFramework") +: (Test / testFrameworks).value', '(Test / testFrameworks).value :+ new TestFramework("fixture.ForeignFramework")')
    settings = replace_once(settings, '    IO.write(directory / "mode", modes.head)', '    IO.write(directory / "mode", modes.head)\n    IO.write(directory / "target.directory", (Test / target).value.toPath.toAbsolutePath.normalize().toString)')
    write_sbt_project(build, settings + listener, '2.0.9', 'addSbtPlugin("io.7mind.izumi" % "sbt-distage-testkit" % ' + json.dumps(args.artifact_version) + ')\n')
    paths = [p for p in sorted(build.rglob('*')) if p.is_file()] + [Path(__file__).resolve(), api_path, fixture_path, mixed_path]
    inputs = [dict(path=str(p), sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths]
    argv = ['direnv', 'exec', str(root), 'sh', '-c', 'exec sbt --server --sbt-version 2.0.9 -java-home "$JDK21" -batch -J-Xmx6G "$@"', 'active-foreign-cancel', '--detach-stdio', 'startServer', 'shell']
    (out / 'command.json').write_text(json.dumps(dict(argv=argv, cwd=str(build), head=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(), inputs=inputs), indent=2) + '\n')
    client = None
    checks = []
    failures = []
    runs = set()
    resources = set()
    audit = build / 'audit'
    log = (out / 'server.log').open('x')
    server = tempfile.TemporaryDirectory(prefix='distage-rpc-', dir=root / 'target')
    process = subprocess.Popen(argv, cwd=build, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, start_new_session=True, env=dict(os.environ, SBT_GLOBAL_SERVER_DIR=server.name))

    def owned_completed():
        target = Path((audit / 'target.directory').read_text())
        reports = list((target / 'test-reports').glob('*fixture.Suite*.xml'))
        streams = list((target / 'distage-events').glob('*.jsonl'))
        if len(reports) != 5 or len(streams) != 1:
            return False
        raw = streams[0].read_text()
        return raw.endswith('\n') and json.loads(raw.splitlines()[-1])['message']['kind'] == 'completed'

    try:
        active = build / 'project/target/active.json'
        api.wait(active.exists, process, 'ACTIVE_FOREIGN_NO_SERVER', api.WAIT_SECONDS)
        client = api.Client(json.loads(active.read_text()))
        initialized = client.await_response(client.send('initialize', dict(processId=os.getpid(), rootUri=build.as_uri(), capabilities={}, initializationOptions=dict(skipAnalysis=True, canWork=False))))
        assert 'result' in initialized
        for fork in [False, True]:
            client.successful('set Test / fork := ' + str(fork).lower())
            for mode in ['normal', 'cancel', 'recovery']:
                client.successful('prepareCancellation ' + mode)
                name = ('fork-' if fork else 'inprocess-') + mode
                request = 'testOnly fixture.Suite* fixture.ForeignSuite'
                identity = client.send('sbt/exec', dict(commandLine=request))
                row = dict(name=name, mode=mode, fork=fork, request=request, execId=identity)
                if mode == 'cancel':
                    api.wait(lambda: (audit / 'foreign.entered').exists() and len(list(audit.glob('*.body'))) == 15 and len(list(audit.glob('*.release'))) == 1 and owned_completed(), process, 'ACTIVE_FOREIGN_PRECONDITION_MISSING', api.WAIT_SECONDS)
                    cancelled = client.send('sbt/cancelRequest', dict(id=api.CANCEL_CURRENT_CHANNEL))
                    time.sleep(api.RELEASE_HOLD_SECONDS)
                    assert not (audit / 'foreign.released').exists() and identity not in client.responses, 'ACTIVE_FOREIGN_COMMAND_RETURNED_BEFORE_RELEASE'
                    (audit / 'foreign.observed-held').write_text('held')
                    (audit / 'allow-foreign-release').write_text('release')
                    row['cancelResponse'] = client.await_response(cancelled)
                row['response'] = client.await_response(identity)
                (audit / 'host-command-returned').write_text('returned')
                client.successful('captureCancellation ' + name)
                checks.append(verify(out / 'cases' / name, row, runs, resources))
                print('ACTIVE_FOREIGN_CASE_OK ' + name, flush=True)
        client.send('sbt/exec', dict(commandLine='shutdown'))
        assert process.wait(timeout=30) == 0
    except BaseException as cause:
        failures.append(repr(cause) + '\n' + traceback.format_exc())
        print(failures[-1], flush=True)
        if audit.is_dir():
            (audit / 'allow-foreign-release').write_text('release')
        if client is not None:
            try:
                client.send('sbt/cancelRequest', dict(id=api.CANCEL_CURRENT_CHANNEL))
                client.send('sbt/exec', dict(commandLine='shutdown'))
                process.wait(timeout=30)
            except BaseException as cleanup:
                failures.append('Cleanup: ' + repr(cleanup))
    finally:
        if audit.is_dir() and not (audit / 'allow-foreign-release').exists():
            (audit / 'allow-foreign-release').write_text('release')
        api.close_server(client, process, out, log, server)
    changed = [r['path'] for r in inputs if hashlib.sha256(Path(r['path']).read_bytes()).hexdigest() != r['sha256']]
    result = dict(exit=int(bool(failures) or bool(changed)), actualExit=process.returncode, scala=args.scala_version, checks=checks, failures=failures, inputsChanged=changed)
    (out / 'completion.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(dict(exit=result['exit'], checks=len(checks))), flush=True)
    raise SystemExit(result['exit'])


if __name__ == '__main__':
    main()
