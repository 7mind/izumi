#!/usr/bin/env python3
from run_cases import BASE, run
import argparse, re
p=argparse.ArgumentParser();p.add_argument('version',type=int);v=p.parse_args().version
bootstrap='set Test / testFrameworks := Seq(new TestFramework("spike.ApplicationFramework"), new TestFramework("spike.OtherFramework"))'
run(v,'fork-whole',[bootstrap,'set Test / fork := true','testOnly spike.SuiteA spike.SuiteB'],6,0,{'spike.SuiteA':3,'spike.SuiteB':3})
lines=(BASE/f'logs/sbt{v}-fork-whole.audit').read_text().splitlines()
assert [x for x in lines if x.startswith('APP ')] == ['APP FORK_WHOLE names=spike.SuiteA,spike.SuiteB args='],lines
assert lines.count('ACQUIRE FORK_WHOLE') == lines.count('RELEASE FORK_WHOLE') == 1,lines
print('PASS target bootstrap: one complete selection, one acquisition/release',flush=True)
groups='set Test / testGrouping := { val tests = (Test / definedTests).value.filter(_.name.startsWith("spike.Suite")).sortBy(_.name); Seq(new Tests.Group("left", tests.take(2), Tests.SubProcess(ForkOptions())), new Tests.Group("right", tests.drop(2), Tests.SubProcess(ForkOptions()))) }'
if v==2: groups = groups.replace('testGrouping := {', 'testGrouping := Def.uncached {')
run(v,'fork-groups',[bootstrap,groups,'testOnly spike.Suite*'],15,0,dict.fromkeys(['spike.Suite'+c for c in 'ABCDE'],3))
lines=(BASE/f'logs/sbt{v}-fork-groups.audit').read_text().splitlines()
assert sorted(x for x in lines if x.startswith('APP ')) == ['APP FORK_WHOLE names=spike.SuiteA,spike.SuiteB args=','APP FORK_WHOLE names=spike.SuiteC,spike.SuiteD,spike.SuiteE args='],lines
assert lines.count('ACQUIRE FORK_WHOLE') == lines.count('RELEASE FORK_WHOLE') == 2,lines
print('PASS configured groups: two applications, two acquisitions/releases',flush=True)

fixture=BASE/('fixture' if v==1 else 'fixture-sbt2')
suite=fixture/'src/test/java/spike/SuiteA.java'
source=suite.read_text();match=re.search(r'REVISION = (\d+)',source)
assert match
suite.write_text(source.replace(match.group(0),'REVISION = '+str(int(match.group(1))+1)))
commands=[bootstrap,'set Test / fork := true']
if v==1:
    commands.append('set Test / testQuick / testFilter := { val inherited = (Test / testQuick / testFilter).value; args => { val selected = Defaults.selectedFilter(args); Seq((name: String) => if (name.startsWith("spike.Suite")) selected.exists(_(name)) else inherited(args).exists(_(name))) } }')
commands+=['testOnly spike.SuiteA -- --one','testQuick spike.SuiteA' if v==1 else 'test spike.SuiteA']
run(v,'fork-partial-safe',commands,4,0,{'spike.SuiteA':3})
lines=(BASE/f'logs/sbt{v}-fork-partial-safe.audit').read_text().splitlines()
assert [x for x in lines if x.startswith('APP ')] == ['APP FORK_WHOLE names=spike.SuiteA args=--one','APP FORK_WHOLE names=spike.SuiteA args='],lines
assert lines.count('ACQUIRE FORK_WHOLE') == lines.count('RELEASE FORK_WHOLE') == 2,lines
print('PASS fork partial safety: one selected body, then all three',flush=True)
