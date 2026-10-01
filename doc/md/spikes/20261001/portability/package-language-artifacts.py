from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import sys
here = Path(__file__).resolve().parent
repo = here.parents[4]
version = sys.argv[1]
output = Path(sys.argv[2]).resolve()
output.mkdir(parents=True, exist_ok=True)
for name in ['fundamentals-basics', 'fundamentals-literals', 'fundamentals-language']:
    classes = repo / f'target/out/jvm/scala-{version}/{name}/classes'
    if not classes.is_dir():
        raise RuntimeError(f'Missing successful compilation output: {classes}')
    with ZipFile(output / f'{name}_{version}.jar', 'w', ZIP_DEFLATED) as jar:
        for source in classes.rglob('*'):
            if source.is_file(): jar.write(source, source.relative_to(classes))
print(output)
