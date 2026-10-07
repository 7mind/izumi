import argparse
import difflib
import hashlib
import json
from pathlib import Path
import re
import subprocess


def sha(data):
    return hashlib.sha256(data).hexdigest()


def without_imports(data):
    return b"".join(line for line in data.splitlines(keepends=True) if not re.match(rb"\s*import\b", line))


def original_blob(repo, original):
    data = subprocess.check_output(["git", "show", original["commit"] + ":" + original["path"]], cwd=repo)
    assert sha(data) == original["sha256"], original
    return data


parser = argparse.ArgumentParser()
parser.add_argument("--capture", type=Path, required=True)
args = parser.parse_args()
fixture = Path(__file__).resolve().parent
repo = fixture.parents[1]
manifest_path = fixture / "manifest.json"
manifest = json.loads(manifest_path.read_text())
args.capture.mkdir()
records = []
inherited_records = []

for row in manifest["sources"]:
    if row["sha256"] is None:
        assert row["eligibility"] == "permitted-ScalaMock-removal", row
        continue
    current = (repo / row["current"]).read_bytes()
    assert sha(current) == row["sha256"], row["current"]
    if row["original"] is None:
        continue
    before = original_blob(repo, row["original"])
    imports_only = without_imports(before) == without_imports(current)
    assert imports_only == row["importsOnly"], row["current"]
    diff = "".join(difflib.unified_diff(before.decode().splitlines(keepends=True), current.decode().splitlines(keepends=True), fromfile=row["original"]["path"], tofile=row["current"]))
    target = args.capture / "diffs" / (row["current"] + ".diff")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(diff)
    records.append(dict(source=row["current"], sourceSha256=sha(current), importsOnly=imports_only, diff=str(target), diffSha256=sha(diff.encode())))

for row in manifest["inheritedSources"]:
    current = (repo / row["currentPath"]).read_bytes()
    assert sha(current) == row["currentSha256"], row["currentPath"]
    before = original_blob(repo, dict(commit=row["originalCommit"], path=row["originalPath"], sha256=row["originalSha256"]))
    fixed = original_blob(repo, dict(commit=row["fixedDevelopCommit"], path=row["fixedDevelopPath"], sha256=row["fixedDevelopSha256"]))
    imports_only = without_imports(before) == without_imports(current)
    assert imports_only == row["importsOnly"], row["currentPath"]
    diff = "".join(difflib.unified_diff(before.decode().splitlines(keepends=True), current.decode().splitlines(keepends=True), fromfile=row["originalPath"], tofile=row["currentPath"]))
    target = args.capture / "diffs" / (row["currentPath"] + ".diff")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(diff)
    inherited_records.append(dict(source=row["currentPath"], sourceSha256=sha(current), fixedDevelopSha256=sha(fixed), importsOnly=imports_only, diff=str(target), diffSha256=sha(diff.encode())))

for row in manifest["preservedOriginals"]:
    current = (fixture / row["source"]).read_bytes()
    assert sha(current) == row["migratedSha256"], row["source"]
    if "declarations" in row:
        before = original_blob(repo, row["original"])
        lines = before.splitlines(keepends=True)
        for declaration in row["declarations"]:
            span = b"".join(lines[declaration["startLine"] - 1:declaration["endLine"]]).removesuffix(b"\n")
            assert sha(span) == declaration["sha256"], declaration
            assert current.count(span) == 1, declaration
    else:
        before = original_blob(repo, dict(commit=row["originalCommit"], path=row["originalPath"], sha256=row["originalSha256"]))
        assert without_imports(before) == without_imports(current), row["source"]

result = dict(actualExit=0, manifestSha256=sha(manifest_path.read_bytes()), sources=records, inheritedSources=inherited_records, preservedOriginals=len(manifest["preservedOriginals"]), scope="Source provenance and complete per-file diffs only; compilation, discovery and terminal outcomes require separate runtime evidence.")
(args.capture / "completion.json").write_text(json.dumps(result, indent=2) + "\n")
print("SOURCE_COMPATIBILITY_PROVENANCE_OK sources=" + str(len(records)) + " inheritedSources=" + str(len(inherited_records)) + " preservedOriginals=" + str(result["preservedOriginals"]))
