import hashlib
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

EXPECTED_ASSERTION_FIXTURE_CHECKS = 100


def audit(commands, evidence):
    records = []
    for command in commands:
        build = Path(command['cwd'])
        platform = command['platform']
        log = (evidence / (command['label'] + '.log')).read_text()
        assert log.count(f'ASSERTION_FIXTURES_OK checks={EXPECTED_ASSERTION_FIXTURE_CHECKS}') == 2
        assert log.count('COVERAGE_MACRO_WITNESS success-and-failure') == 2
        assert log.count('COVERAGE_PHYSICAL_BODY suite=LeftSuite ') == 2
        assert log.count('COVERAGE_PHYSICAL_BODY suite=RightSuite ') == 2
        if platform == 'jvm':
            pids = re.findall(r'COVERAGE_PHYSICAL_BODY suite=\S+ fork=(\d+)', log)
            assert len(pids) == 4 and len(set(pids)) == (4 if command['fork'] else 1)
        reports = list((build / 'coverage-capture').rglob('scoverage.xml'))
        assert len(reports) == 3
        for report in reports:
            classes = {node.attrib['name']: node for node in ET.parse(report).getroot().findall('.//class')}
            witnesses = set(classes).intersection({'coveragefixture.BranchWitness', 'coveragefixture.SecondWitness'})
            assert witnesses
            for name in witnesses:
                node = classes[name]
                statements = node.findall('.//statement')
                branches = [row for row in statements if row.attrib['branch'] == 'true']
                assert len(branches) == 2 and sum(int(row.attrib['invocation-count']) > 0 for row in branches) == 1
                assert float(node.attrib['branch-rate']) == 50
                assert all(int(row.attrib['invocation-count']) > 0 for row in statements if int(row.attrib['line']) in [5, 6, 7])
                assert all(int(row.attrib['invocation-count']) == 0 for row in statements if int(row.attrib['line']) in [8, 9, 10])
            if 'coveragefixture.MacroWitness' in classes:
                macro = classes['coveragefixture.MacroWitness']
                assert float(macro.attrib['branch-rate']) == 100
                assert macro.attrib['statement-rate'] == ('100.00' if command['scala'].startswith('3.') else '88.89')
        version = next(value.split('=', 1)[1] for value in command['argv'] if value.startswith('-Dcoverage.version='))
        binary = '3' if command['scala'].startswith('3.') else '2.13'
        target = {'jvm': 'jvm', 'js': 'sjs1', 'native': 'native0.5'}[platform]
        suffix = binary if platform == 'jvm' else target + '_' + binary
        publications = []
        for module in ['witness', 'right']:
            directory = Path.home() / '.ivy2/local/izumi.local.coverage' / (module + '_' + suffix) / version
            jars = list((directory / 'jars').glob('*.jar'))
            assert len(jars) == 1
            classes = build / 'target/out' / target / ('scala-' + command['scala']) / module / 'classes'
            with zipfile.ZipFile(jars[0]) as archive:
                entries = [name for name in archive.namelist() if name.endswith(('.class', '.tasty', '.nir', '.sjsir'))]
                assert entries and all(archive.read(name) == (classes / name).read_bytes() for name in entries)
                assert all(not any(marker in archive.read(name) for marker in [b'scoverage/Invoker', b'scoverage.Invoker', b'scala/runtime/coverage']) for name in entries)
            poms = list((directory / 'poms').glob('*.pom'))
            assert len(poms) == 1 and 'scoverage' not in poms[0].read_text()
            publications.append(dict(module=module, sha256=hashlib.sha256(jars[0].read_bytes()).hexdigest()))
        records.append(dict(lane=command['label'], reports=[dict(path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest()) for path in reports], normalPublications=publications))
    return dict(lanes=records, reports=sum(len(row['reports']) for row in records), normalPublications=sum(len(row['normalPublications']) for row in records))
