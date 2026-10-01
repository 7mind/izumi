#!/usr/bin/env python3
"""Execute each isolated version's complete behavioral host fixture matrix."""
from pathlib import Path
import subprocess
from run_cases import BASE
for version in [1,2]:
    fixture=BASE/('fixture' if version==1 else 'fixture-sbt2')
    inputs=[fixture/name for name in ['di-only.txt','src/test/java/spike/WiringOnly.java','src/test/java/spike/SuiteA.java']]
    originals={path:path.read_bytes() for path in inputs}
    try:
        (fixture/'di-only.txt').write_text('initial\n')
        (fixture/'src/test/java/spike/WiringOnly.java').write_text('package spike; public final class WiringOnly { public static String value() { return "implementation-one"; } }\n')
        for script in ['run_cases.py','extra-cases.py','fork-cases.py','dynamic-cases.py']:
            print(f'RUN python {script} {version}',flush=True)
            subprocess.run(['python',script,str(version)],cwd=BASE,check=True)
    finally:
        for path,content in originals.items(): path.write_bytes(content)
print('PASS complete SBT 1.13.0 / SBT 2.0.9 matrix',flush=True)
