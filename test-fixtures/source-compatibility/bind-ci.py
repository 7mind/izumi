import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET
import zipfile


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def class_source(data):
    assert data[:4] == b"\xca\xfe\xba\xbe"
    offset = 8

    def u2():
        nonlocal offset
        value = struct.unpack_from(">H", data, offset)[0]
        offset += 2
        return value

    def u4():
        nonlocal offset
        value = struct.unpack_from(">I", data, offset)[0]
        offset += 4
        return value

    pool = [None] * u2()
    index = 1
    while index < len(pool):
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = u2()
            pool[index] = data[offset:offset + length].decode("utf-8", "replace")
            offset += length
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = u2()
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(tag)
        index += 1
    flags = u2()
    this = u2()
    u2()
    interfaces = u2()
    offset += 2 * interfaces

    def attributes():
        nonlocal offset
        values = []
        for _ in range(u2()):
            name = pool[u2()]
            length = u4()
            values.append((name, data[offset:offset + length]))
            offset += length
        return values

    for _ in range(2):
        for _ in range(u2()):
            offset += 6
            attributes()
    source = next((pool[struct.unpack(">H", value)[0]] for name, value in attributes() if name == "SourceFile"), None)
    return pool[pool[this]].replace("/", "."), source, flags


parser = argparse.ArgumentParser()
parser.add_argument("--ci", type=Path, nargs="+", required=True)
parser.add_argument("--capture", type=Path, required=True)
args = parser.parse_args()
fixture = Path(__file__).resolve().parent
repo = fixture.parents[1]
manifest_path = fixture / "manifest.json"
manifest = json.loads(manifest_path.read_text())
sources = {row["current"]: dict(sha256=row["sha256"], eligibility=row["eligibility"]) for row in manifest["sources"] if row["sha256"] is not None}
for row in manifest["inheritedSources"]:
    assert row["currentPath"] not in sources
    sources[row["currentPath"]] = dict(sha256=row["currentSha256"], eligibility=row["classification"])
args.capture.mkdir()
index = []

for cap in args.ci:
    command_path = cap / "command.json"
    completion_path = cap / "completion.json"
    command = json.loads(command_path.read_text())
    completion = json.loads(completion_path.read_text())
    assert completion["actualExit"] == 0 and not completion["inputsChanged"], cap
    wt = Path(command["cwd"])
    platform = command["platform"].replace("-nojvm", "")
    compiler = "2.13.18" if command["scala"] == "2.13" else "3.9.0"
    build_path = wt / "build.sbt"
    assert sha(build_path) == command["inputSha256"][str(build_path)]
    build = build_path.read_text()
    projects = {}
    pattern = r'^lazy val `([^`]+)` = (crossProject\(([^)]*)\)\.crossType\([^\n]+|project)\.in\(file\("([^"]+)"\)\)'
    for match in re.finditer(pattern, build, re.M):
        projects[match[4]] = dict(name=match[1], declaration=match[0], platforms=[p.lower().replace("platform", "") for p in match[3].split(", ")] if match[3] else ["jvm"])
    cases = defaultdict(list)
    for report in completion["reports"]:
        path = Path(report["path"])
        assert sha(path) == report["sha256"]
        xml = ET.parse(path).getroot()
        for test in xml.findall("testcase"):
            status = "failed" if test.find("failure") is not None or test.find("error") is not None else "skipped" if test.find("skipped") is not None else "passed"
            cases[xml.attrib["name"]].append(dict(name=test.attrib["name"], outcome=status, report=str(path), reportSha256=report["sha256"]))
    classes = defaultdict(list)
    jars = {}
    output_platform = dict(jvm="jvm", js="sjs1", native="native0.5")[platform]
    for jar in sorted((wt / "target/out" / output_platform / ("scala-" + compiler)).glob("*/*-tests.jar")):
        jars[str(jar)] = sha(jar)
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                if not entry.endswith(".class"):
                    continue
                data = archive.read(entry)
                name, filename, flags = class_source(data)
                assert name.replace(".", "/") + ".class" == entry
                if filename:
                    package = name.rsplit(".", 1)[0] if "." in name else ""
                    classes[(jar.parent.name, package, filename)].append(dict(name=name, entry=entry, classSha256=hashlib.sha256(data).hexdigest(), jar=str(jar), abstract=bool(flags & 0x0400), reportedCases=cases.get(name, [])))
    bindings = []
    for path, row in sources.items():
        source = repo / path
        assert sha(source) == row["sha256"], path
        parts = Path(path).parts
        project_path = "/".join(parts[:2])
        project = projects.get(project_path)
        reason = None
        if project is None or platform not in project["platforms"]:
            reason = "project absent from this generated platform build"
        elif any("." + p in parts for p in ("jvm", "js", "native") if p != platform):
            reason = "other platform source directory"
        elif (
            any(part in parts for part in ("scala-jvm-native", "scala-jvm-native-2", "scala-jvm-native-3")) and platform == "js"
        ) or (
            any(part in parts for part in ("scala-js-native", "scala-js-native-2", "scala-js-native-3")) and platform == "jvm"
        ):
            reason = "other platform source directory"
        elif (
            any(part in parts for part in ("scala-2", "scala-jvm-native-2", "scala-js-native-2")) and command["scala"] != "2.13"
        ) or (
            any(part in parts for part in ("scala-3", "scala-jvm-native-3", "scala-js-native-3")) and command["scala"] != "3"
        ):
            reason = "other compiler source directory"
        elif "scala-derivation" in parts and platform == "native" and command["scala"] == "2.13":
            assert 'if (scalaVersion.value.startsWith("3.")) Seq(file("fundamentals/fundamentals-json-circe-test/src/test/scala-derivation")' in build
            reason = "generated Native settings include derivation only on Scala3"
        package_match = re.search(r"^package\s+([\w.]+)", source.read_text(), re.M)
        package = package_match[1] if package_match else ""
        found = classes.get((parts[1], package, source.name), []) if reason is None else []
        input_matches = command["inputSha256"].get(str(wt / path)) == row["sha256"]
        assert input_matches, path
        bindings.append(dict(source=path, sourceSha256=row["sha256"], eligibility=row["eligibility"], frozenSourceInputMatches=input_matches, project=project, excludedReason=reason, compiledClasses=found, reportedCases=sum(len(c["reportedCases"]) for c in found)))
    result = dict(actualExit=0, capture=str(cap), head=command["head"], platform=platform, compiler=compiler, manifestSha256=sha(manifest_path), commandSha256=sha(command_path), completionSha256=sha(completion_path), buildSha256=sha(build_path), artifactSha256=jars, bindings=bindings, scope="Exact frozen source, generated project platform, class SourceFile and raw terminal XML bindings. Compilation or source presence alone does not establish execution; unreported nested/negative suites require their parent control evidence.")
    target = args.capture / (cap.name + ".json")
    target.write_text(json.dumps(result, indent=2) + "\n")
    summary = dict(capture=str(cap), bindings=len(bindings), activeSources=sum(b["excludedReason"] is None for b in bindings), compiledSources=sum(bool(b["compiledClasses"]) for b in bindings), reportedSources=sum(b["reportedCases"] > 0 for b in bindings), unresolvedCompiledSources=[b["source"] for b in bindings if b["excludedReason"] is None and not b["compiledClasses"]], bindingSha256=sha(target))
    index.append(summary)
    print(json.dumps(summary), flush=True)
(args.capture / "completion.json").write_text(json.dumps(dict(actualExit=0, manifestSha256=sha(manifest_path), captures=index), indent=2) + "\n")
