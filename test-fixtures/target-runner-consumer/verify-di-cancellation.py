#!/usr/bin/env python3
from collections import Counter
import json, re, shutil, sys
from pathlib import Path
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import load_module, run_lanes
from fixture_targets import target_parser, prepare_lanes

FIXTURE=Path(__file__).resolve().parent
TEMPLATE=FIXTURE/'di-failures'
HELD=FIXTURE/'di-cancellation/Held.scala'
sys.path.insert(0,str(FIXTURE))
from verify import sha
di = load_module('di', FIXTURE / 'verify-di.py')
policy = load_module('policy', FIXTURE / 'verify-policy.py')

SETTINGS='''
  Test / scalacOptions ++= (if (scalaVersion.value.startsWith("3.")) Seq("-Yretain-trees", "-Xmax-inlines:64") else Seq.empty),
  libraryDependencies ++= Seq(
    "org.typelevel" % ("cats-effect_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % "3.7.1" % Test,
    ("dev.zio" % ("zio_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % "2.1.26" % Test).excludeAll(ExclusionRule(organization = "dev.zio", name = "izumi-reflect_" + candidatePlatform.value + "_" + scalaBinaryVersion.value)),
  ),
  libraryDependencySchemes ++= (if (candidatePlatform.value == "native0.5") Seq("org.scala-native" % ("test-interface_native0.5_" + scalaBinaryVersion.value) % "always") else Seq.empty),
'''

def audit(manifest,out):
    records=[];runs=set();owners=set()
    for command in manifest['commands']:
        build=Path(command['cwd']);log=(out/(command['scala']+'-'+command['platform']+'.log')).read_text()
        for case in command['cases']:
            label=case['name'];segment=log.split('SDK_POLICY_BEGIN '+label+'\n')[1].split('SDK_POLICY_END '+label+'\n')[0]
            files=list((build/'captures'/label/'frames').glob('*.jsonl'));assert len(files)==1,(label,'applications')
            messages=[json.loads(line)['message'] for line in files[0].read_text().splitlines()];assert messages[-1]['kind']=='completed',(label,'completion')
            outcome=messages[-1]['outcome'];assert outcome['cancelled']==(label=='cancelled') and not outcome['failures'];assert outcome['run'] not in runs;runs.add(outcome['run'])
            results=outcome['results'];assert len(results)==21 and len({json.dumps(row['id'],sort_keys=True) for row in results})==21
            assert set(row['status'] for row in results)<=set(['succeeded','cancelled'])
            bodies=re.findall(r'SDK_DI_BODY suite=(\S+) test=(\d+) effect=(\S+) owner=(\S+) revision=(\S+) snapshot=(\S+) repo=(\S+)',segment)
            physical=Counter((row[0],{'1':'first','2':'second','3':'third'}[row[1]]) for row in bodies)
            assert physical==Counter((row['id']['suite'],row['id']['path'][-1]) for row in results if row['status']=='succeeded'),(label,'physical terminal identities')
            if label!='cancelled':assert len(bodies)==21 and all(row['status']=='succeeded' for row in results)
            acquired=re.findall(r'SDK_DI_ACQUIRE owner=(\S+)',segment);released=re.findall(r'SDK_DI_RELEASE owner=(\S+)',segment);assert Counter(acquired)==Counter(released) and not owners.intersection(released);owners.update(released)
            held=re.findall(r'SDK_DI_HELD_ACQUIRE owner=(\S+)',segment);entered=re.findall(r'SDK_DI_FINALIZER_ENTER owner=(\S+)',segment);exited=re.findall(r'SDK_DI_FINALIZER_EXIT owner=(\S+)',segment);assert len(held)==1 and held==entered==exited and not owners.intersection(held);owners.update(held)
            assert segment.index('SDK_DI_FINALIZER_ENTER')<segment.index('SDK_DI_FINALIZER_EXIT')<segment.index('SDK_POLICY_CLEANUP')
            if label=='cancelled':assert segment.index('SDK_DI_FINALIZER_ENTER')<segment.index('SDK_EXECUTION_INTERRUPT')<segment.index('SDK_DI_FINALIZER_HELD_CHECK')<segment.index('SDK_DI_FINALIZER_EXIT') and segment.count('SDK_EXPECTED_CANCELLATION_FAILURE')==1
            events=[row for row in messages if row['kind']=='event'];assert [int(row['sequence']) for row in events]==list(range(len(events)))
            assert Counter((row['event']['test']['suite'],row['event']['test']['path'][-1]) for row in events if row['event']['kind']=='testStarted')==physical
            assert Counter(json.dumps(row['event']['result'],sort_keys=True) for row in events if row['event']['kind']=='testCompleted')==Counter(json.dumps(row,sort_keys=True) for row in results)
            assert events[-1]['event']['outcome']==outcome
            xml={};suites=set()
            for path in (build/'captures'/label/'xml').glob('*.xml'):
                node=ET.parse(path).getroot();suite=node.attrib['name'];assert suite not in suites;suites.add(suite);errors=0
                for item in node.findall('.//testcase'):
                    if item.find('error') is not None:errors+=1;continue
                    assert item.find('failure') is None
                    key=(suite,item.attrib['name']);assert key not in xml;xml[key]='cancelled' if item.find('skipped') is not None else 'succeeded'
                assert errors==(1 if label=='cancelled' else 0)
            assert xml=={(row['id']['suite'],' '.join(row['id']['path'])):row['status'] for row in results} and len(suites)==7
            assert segment.count('SDK_POLICY_SETUP')==segment.count('SDK_POLICY_CLEANUP')==1
            records.append(dict(scala=command['scala'],platform=command['platform'],case=label,bodies=len(bodies),resources=len(acquired)+len(held),run=outcome['run']))
    return dict(contexts=len(records),bodies=sum(row['bodies'] for row in records),resources=sum(row['resources'] for row in records),records=records)


def prepare_lane(args, compiler, platform, paths):
    suffix=('sjs1' if platform=='js' else 'native0.5')+'_'+('3' if compiler.startswith('3.') else '2.13')
    command,prepared=di.prepare_di(args,compiler,platform,paths,TEMPLATE,di.failure_suites(TEMPLATE),True)
    build=Path(command['cwd'])
    shutil.copyfile(HELD,build/'shared/Held.scala')
    plugin=build/'shared/FixturePlugin.scala';plugin.write_text(plugin.read_text().replace('Seq(new FixturePlugin)','Seq(new FixturePlugin, new HeldPlugin)'))
    suites=build/'shared/Suites.scala';suite_text=suites.read_text().replace('final class EffectCats extends Spec1[IO] with Configured','final class EffectCats extends Spec1[IO] with HeldConfigured')
    for index in [1,2,3]:
        suite_text=suite_text.replace('(value: SharedResource) => IO(record('+str(index)+', value))','(value: SharedResource, held: HeldResource) => IO { require(held != null); record('+str(index)+', value) }')
    suites.write_text(suite_text)
    platform_source=build/('platform-'+platform)/'Platform.scala'
    definition=build/'build.sbt';text=definition.read_text().replace('distage-test-runner_','distage-testkit-runner_')
    text=text.replace('val common = Seq(',policy.CONTROLS+'\nval common = Seq(\n'+policy.SETTINGS+SETTINGS)
    definition.write_text(text)
    marker=build/'held-finalizer.txt'
    command['argv'].insert(7,'-Dcandidate.finalizer='+str(marker))
    mark_method=('scala.scalajs.js.Dynamic.global.require("fs").writeFileSync('+json.dumps(str(marker))+', state)' if platform=='js' else '{ val writer = new java.io.PrintWriter('+json.dumps(str(marker))+'); try writer.print(state) finally writer.close() }')
    platform_source.write_text(platform_source.read_text().replace('object Platform {','object Platform {\n  def finalizerMarker(state: String): Unit = { '+mark_method+'; () }\n'))
    probe=build/'project/ProductionInterruption.scala';probe_text=probe.read_text()
    old_condition='(files() -- prior).exists(path => Files.readString(path).contains("\\\"testStarted\\\""))'
    assert old_condition in probe_text,old_condition
    probe_text=probe_text.replace(old_condition,'Files.exists(Paths.get(sys.props("candidate.finalizer"))) && Files.readString(Paths.get(sys.props("candidate.finalizer"))) == "enter"')
    probe_text=probe_text.replace('caller.interrupt()','caller.interrupt()\n                    Thread.sleep(200L)\n                    require(Files.readString(Paths.get(sys.props("candidate.finalizer"))) == "enter", "Finalizer was not held during cancellation")\n                    require(!(files() -- prior).exists(path => Files.readString(path).contains("\\\"kind\\\":\\\"completed\\\"")), "Application completed before its held finalizer")\n                    println("SDK_DI_FINALIZER_HELD_CHECK")')
    probe_text=probe_text.replace('require(interrupted.get(),','require(Files.readString(Paths.get(sys.props("candidate.finalizer"))) == "exit", "SDK returned before DI finalizer exit")\n                require(interrupted.get(),')
    probe.write_text(probe_text)
    all_suites={name:[1,2,3] for name in ['candidate.Suite'+letter for letter in 'ABCDE']+['candidate.EffectCats','candidate.EffectZIO']}
    rows=[('normal','testFull',all_suites,3,False,[],'one','alpha','dummy'),('cancelled','expectCandidateCancellation',all_suites,3,False,[platform+'/armCandidateInterruption'],'one','alpha','dummy'),('recovery','test',all_suites,3,False,[platform+'/disarmCandidateInterruption'],'one','alpha','dummy')]
    requests=[];cases=[]
    for name,request,suites,resources,unmemoized,before,revision,snapshot,repo in rows:
        cases.append(dict(name=name,suites=suites,resources=resources,unmemoized=unmemoized,revision=revision,snapshot=snapshot,repo=repo));requests+=before+[platform+'/preparePolicy '+name,platform+'/'+request,platform+'/collectPolicy '+name]
    command['cases']=cases;command['argv']=command['argv'][:command['argv'].index(platform+'/testFull')]+requests
    prepared += di.runtime_publication_inputs('distage-testkit-runner_'+suffix,args.artifact_version)
    return command, prepared

def main():
    parser = target_parser()
    parser.add_argument('--host-threads', choices=['1', '2'], required=True)
    args = parser.parse_args()
    args.repo_root=args.repo_root.resolve();args.logical_suite_alias=False
    commands, inputs, out = prepare_lanes(args, prepare_lane, [*TEMPLATE.glob('*.scala'), HELD, Path(__file__).resolve()])
    (out/'commands.json').write_text(json.dumps(dict(inputs=inputs,commands=commands),indent=2)+'\n')
    results = run_lanes(commands, out, 2400)
    mutable={str(Path(command['cwd'])/'shared/FixturePlugin.scala') for command in commands}
    expected_final=(TEMPLATE/'FixturePlugin.scala').read_text().replace('Seq(new FixturePlugin)','Seq(new FixturePlugin, new HeldPlugin)')
    wrong_edits=[path for path in mutable if Path(path).read_text()!=expected_final]
    external_inputs={str(Path(command['cwd'])/'external-configuration.txt') for command in commands}
    wrong_edits += [path for path in external_inputs if Path(path).read_text()!='']; mutable.update(external_inputs)
    changed=wrong_edits+[row['path'] for row in inputs if row['path'] not in mutable and sha(Path(row['path']))!=row['sha256']]
    (out/'completion.json').write_text(json.dumps(dict(lanes=results,inputsChanged=changed,intentionalEdits=sorted(mutable)),indent=2)+'\n')
    assert not changed and all(row['actualExit']==0 for row in results),results
    report=audit(dict(commands=commands),out)
    (out/'audit.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({key:value for key,value in report.items() if key!='records'}),flush=True)
if __name__=='__main__':main()
