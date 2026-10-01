from pathlib import Path
import re
import subprocess
import sys
here = Path(__file__).resolve().parent
repo = here.parents[4]
mode = sys.argv[1]
git_dir = subprocess.check_output(['git', 'rev-parse', '--absolute-git-dir'], cwd=repo, text=True).strip()
common_dir = subprocess.check_output(['git', 'rev-parse', '--git-common-dir'], cwd=repo, text=True).strip()
if Path(git_dir).resolve() == (repo / common_dir).resolve():
    raise RuntimeError('Run compiler experiments in a disposable git worktree; this script rewrites the pinned build.sbt')
original = subprocess.check_output(['git', 'show', '4feabed5857067d051e357bc1aa62bbb68c96892:build.sbt'], cwd=repo, text=True)
if mode == 'baseline33':
    original, count = re.subn(r'      case \(_, "3\.7\.4"\) => Seq\(.*?(?=      case \(_, _\) => Seq.empty)', '''      case (_, v) if v.startsWith("3.") => Seq(
        "-source:3.3", "-Ykind-projector:underscores", "-release:17", "-Yretain-trees", "-no-indent", "-Xmax-inlines:64"
      )
''', original, flags=re.S)
    if count == 0: raise RuntimeError('No Scala3 options blocks found')
elif mode in ['scala39', 'scala39-backend1', 'scala39-backend1-no-max-inlines']:
    original = original.replace('case (_, "3.7.4") => Seq(', 'case (_, v) if v == "3.7.4" || v == "3.9.0" => Seq(').replace('"-source:3.7",', 's"-source:${if (scalaVersion.value == "3.9.0") "3.9" else "3.7"}",')
    if mode in ['scala39-backend1', 'scala39-backend1-no-max-inlines']:
        original = original.replace('math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString,', '(if (scalaVersion.value == "3.9.0") "1" else math.min(16, math.max(1, sys.runtime.availableProcessors() - 1)).toString),')
    if mode == 'scala39-backend1-no-max-inlines':
        # Control for the first round's decoder failure: the Native fixture lacked this repository flag.
        original, count = re.subn(r'^        "-Xmax-inlines:64",\n', '', original, flags=re.M)
        if count == 0: raise RuntimeError('No -Xmax-inlines:64 options found')
elif mode != 'original': raise RuntimeError(f'Unknown mode {mode}')
(repo / 'build.sbt').write_text(original)
