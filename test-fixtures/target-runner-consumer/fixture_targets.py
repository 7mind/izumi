"""Preparation and input ownership for the published target consumer matrix."""

from pathlib import Path
from argparse import ArgumentParser, Namespace
from collections.abc import Callable

from fixture_harness import scala_consumer_parser, sha, InputDigest, LaneCommand


def target_parser() -> ArgumentParser:
    parser = scala_consumer_parser(multiple=True)
    parser.add_argument('--production-host-version', required=True)
    return parser


def prepare_lanes(args: Namespace, prepare_lane: Callable[[Namespace, str, str, list[Path]], tuple[LaneCommand, list[InputDigest]]], extra_inputs: list[Path]) -> tuple[list[LaneCommand], list[InputDigest], Path]:
    root, out = args.repo_root.resolve(), args.evidence_dir.resolve()
    out.mkdir()
    fixture = root / 'test-fixtures/target-runner-consumer'
    paths = [path for path in fixture.rglob('*') if path.is_file() and path.suffix in ['.scala', '.sbt', '.properties', '.py']]
    inputs: list[InputDigest] = [dict(path=str(path), sha256=sha(path)) for path in paths]
    inputs.extend(dict(path=str(path), sha256=sha(path)) for path in extra_inputs)
    commands: list[LaneCommand] = []
    for compiler in args.scala_version:
        for platform in ['js', 'native']:
            command, prepared = prepare_lane(args, compiler, platform, paths)
            build = Path(command['cwd'])
            inputs.extend(row for row in prepared if not Path(row['path']).is_relative_to(build))
            inputs.extend(dict(path=str(path), sha256=sha(path)) for path in build.rglob('*') if path.is_file())
            commands.append(command)
    return commands, inputs, out

