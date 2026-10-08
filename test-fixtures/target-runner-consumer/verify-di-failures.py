#!/usr/bin/env python3
from collections import Counter
import json, re, sys
from pathlib import Path
import xml.etree.ElementTree as ET
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_harness import load_module, run_lanes, execution_stream
from fixture_targets import target_parser, prepare_lanes

FIXTURE=Path(__file__).resolve().parent
TEMPLATE=FIXTURE/'di-failures'
sys.path.insert(0,str(FIXTURE))
from verify import sha
di = load_module('di', FIXTURE / 'verify-di.py')
policy = load_module('policy', FIXTURE / 'verify-policy.py')

CONTROLS='''
val expectProviderFailure = taskKey[Unit]("Require attributed provider failure")
val editExternalConfiguration = inputKey[Unit]("Change untracked configuration input")
'''
SETTINGS='''
  Test / scalacOptions ++= (if (scalaVersion.value.startsWith("3.")) Seq("-Yretain-trees", "-Xmax-inlines:64") else Seq.empty),
  libraryDependencies ++= Seq(
    "org.typelevel" % ("cats-effect_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % "3.7.1" % Test,
    ("dev.zio" % ("zio_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % "2.1.26" % Test).excludeAll(ExclusionRule(organization = "dev.zio", name = "izumi-reflect_" + candidatePlatform.value + "_" + scalaBinaryVersion.value)),
  ),
  libraryDependencySchemes ++= (if (candidatePlatform.value == "native0.5") Seq("org.scala-native" % ("test-interface_native0.5_" + scalaBinaryVersion.value) % "always") else Seq.empty),
  expectProviderFailure := Def.uncached {
    (Test / testFull).result.value match {
      case Result.Inc(_) => println("SDK_DI_EXPECTED_FAILURE")
      case Result.Value(_) => throw new IllegalStateException("Provider failure command unexpectedly succeeded")
    }
  },
  editExternalConfiguration := Def.uncached {
    val values = sbt.complete.DefaultParsers.spaceDelimited("snapshot").parsed
    require(values.size == 1 && Set("gamma", "delta", "body-failure", "release-failure").contains(values.head), "Unsupported external configuration snapshot")
    IO.write(fixtureRoot / "external-configuration.txt", values.head)
    println("SDK_DI_EDIT kind=external snapshot=" + values.head)
  },
'''

def audit(manifest,out):
    records=[];runs=set();owners=set()
    for command in manifest['commands']:
     build=Path(command['cwd']);log=(out/(command['scala']+'-'+command['platform']+'.log')).read_text()
     for case in command['cases']:
      label=case['name'];segment=log.split('SDK_POLICY_BEGIN '+label+'\n')[1].split('SDK_POLICY_END '+label+'\n')[0];capture=build/'captures'/label
      expected=Counter((suite,'equal display name should '+{1:'first',2:'second',3:'third'}[index]) for suite,indices in case['suites'].items() for index in indices)
      streams=list((capture/'frames').glob('*.jsonl'));assert len(streams)==1
      events, outcome = execution_stream(streams[0].read_text())
      assert not outcome['cancelled'] and outcome['run'] not in runs;runs.add(outcome['run'])
      results=outcome['results'];identity=lambda value:(value['id']['suite'],' '.join(value['id']['path']))
      assert Counter(map(identity,results))==expected,(label,'selected terminal identities',len(results))
      assert not any(row['phase']=='transport' for row in outcome['failures']),(label,'unrelated invariant failure')
      assert Counter(json.dumps(row['event']['result'],sort_keys=True) for row in events if row['event']['kind']=='testCompleted')==Counter(json.dumps(row,sort_keys=True) for row in results)
      assert events[-1]['event']['outcome']==outcome
      bodies=re.findall(r'SDK_DI_BODY suite=(\S+) test=(\d+) effect=(\S+) owner=(\S+) revision=(\S+) snapshot=(\S+) repo=(\S+)',segment)
      physical=Counter((row[0],'equal display name should '+{'1':'first','2':'second','3':'third'}[row[1]]) for row in bodies)
      acquired=re.findall(r'SDK_DI_ACQUIRE owner=(\S+) revision=(\S+) snapshot=(\S+) repo=(\S+)',segment);released=re.findall(r'SDK_DI_RELEASE owner=(\S+)',segment)
      assert Counter(row[0] for row in acquired)==Counter(released) and not owners.intersection(released);owners.update(released)
      for suite,index,effect,owner,revision,snapshot,repo in bodies:
       if effect!='plain':assert owner in released and (revision,snapshot,repo)==('one',case['snapshot'],'dummy')
      if label=='body-failure':
       failed=[row for row in results if row['status']=='failed'];assert len(failed)==1 and identity(failed[0])==('candidate.EffectCats','equal display name should third')
       assert failed[0]['failure']['phase']=='test' and failed[0]['failure']['message']=='SDK_DI_BODY_FAILURE'
       assert Counter(row['status'] for row in results)==Counter(succeeded=20,failed=1) and not outcome['failures'] and physical==expected and len(acquired)==3
      elif label=='release-failure':
       assert outcome['failures'] and all(row['phase']=='finalization' and row['message'].startswith('SDK_DI_RELEASE_FAILURE owner=') for row in outcome['failures'])
       assert set(row['status'] for row in results)<=set(['succeeded','cancelled'])
       assert physical==Counter(identity(row) for row in results if row['status']=='succeeded') and 1<=len(acquired)<=3
       for row in results:
        if row['status']=='cancelled':assert row['failure'] and row['failure']['phase']=='finalization'
      else:assert all(row['status']=='succeeded' for row in results) and not outcome['failures'] and physical==expected and len(acquired)==3
      started=Counter((row['event']['test']['suite'],' '.join(row['event']['test']['path'])) for row in events if row['event']['kind']=='testStarted');assert started==physical,(label,'attempted identities')
      xml_results={};groups=set();errors=0
      for path in (capture/'xml').glob('*.xml'):
       root=ET.parse(path).getroot();suite=root.attrib['name'];assert suite not in groups;groups.add(suite)
       suite_errors=0
       for item in root.findall('.//testcase'):
        if item.find('error') is not None:suite_errors+=1;continue
        status='failed' if item.find('failure') is not None else 'cancelled' if item.find('skipped') is not None else 'succeeded'
        key=(suite,item.attrib['name']);assert key not in xml_results;xml_results[key]=status
       assert suite_errors==len(outcome['failures']),(label,suite,'run failure attribution');errors+=suite_errors
      assert groups==set(case['suites']) and xml_results=={identity(row):row['status'] for row in results},(label,'XML terminal identities/statuses')
      assert segment.count('SDK_POLICY_SETUP')==segment.count('SDK_POLICY_CLEANUP')==1
      if label.endswith('-failure'):assert segment.count('SDK_DI_EXPECTED_FAILURE')==1
      records.append(dict(scala=command['scala'],platform=command['platform'],case=label,bodies=len(bodies),terminalRecords=len(results),resources=len(acquired),xmlErrors=errors,statuses=dict(Counter(row['status'] for row in results)),run=outcome['run'],captures=[dict(path=str(path),sha256=sha(path)) for path in capture.rglob('*') if path.is_file()]))
    report=dict(contexts=len(records),bodies=sum(row['bodies'] for row in records),terminalRecords=sum(row['terminalRecords'] for row in records),resources=sum(row['resources'] for row in records),records=records)
    return report


def prepare_lane(args, compiler, platform, paths):
    suffix=('sjs1' if platform=='js' else 'native0.5')+'_'+('3' if compiler.startswith('3.') else '2.13')
    command,prepared=di.prepare_di(args,compiler,platform,paths,TEMPLATE,di.failure_suites(TEMPLATE),False)
    build=Path(command['cwd'])
    definition=build/'build.sbt';text=definition.read_text().replace('ProductionJsInterruptionPlugin','DistageTestkitJsPlugin').replace('ProductionNativeInterruptionPlugin','DistageTestkitNativePlugin').replace('distage-test-runner_','distage-testkit-runner_')
    text=text.replace('val common = Seq(',policy.CONTROLS+CONTROLS+'\nval common = Seq(\n'+policy.SETTINGS+SETTINGS)
    definition.write_text(text)
    all_suites={name:[1,2,3] for name in ['candidate.Suite'+letter for letter in 'ABCDE']+['candidate.EffectCats','candidate.EffectZIO']}
    rows=[('full','testFull',all_suites,3,False,[],'one','alpha','dummy'),('body-failure','expectProviderFailure',all_suites,3,False,[platform+'/editExternalConfiguration body-failure'],'one','body-failure','dummy'),('body-recovery','test',all_suites,3,False,[platform+'/editExternalConfiguration gamma'],'one','gamma','dummy'),('release-failure','expectProviderFailure',all_suites,3,False,[platform+'/editExternalConfiguration release-failure'],'one','release-failure','dummy'),('release-recovery','testQuick',all_suites,3,False,[platform+'/editExternalConfiguration delta'],'one','delta','dummy')]
    requests=[];cases=[]
    for name,request,suites,resources,unmemoized,before,revision,snapshot,repo in rows:
        cases.append(dict(name=name,suites=suites,resources=resources,unmemoized=unmemoized,revision=revision,snapshot=snapshot,repo=repo));requests+=before+[platform+'/preparePolicy '+name,platform+'/'+request,platform+'/collectPolicy '+name]
    command['cases']=cases;command['argv']=command['argv'][:command['argv'].index(platform+'/testFull')]+requests
    prepared += di.runtime_publication_inputs('distage-testkit-runner_'+suffix,args.artifact_version)
    return command, prepared

def main():
    parser = target_parser()
    args = parser.parse_args()
    args.repo_root=args.repo_root.resolve();args.host_threads='2';args.logical_suite_alias=False
    commands, inputs, out = prepare_lanes(args, prepare_lane, [*TEMPLATE.glob('*.scala'), Path(__file__).resolve()])
    (out/'commands.json').write_text(json.dumps(dict(inputs=inputs,commands=commands),indent=2)+'\n')
    results = run_lanes(commands, out, 2400)
    mutable={str(Path(command['cwd'])/'shared/FixturePlugin.scala') for command in commands}
    expected_final=(TEMPLATE/'FixturePlugin.scala').read_text()
    wrong_edits=[path for path in mutable if Path(path).read_text()!=expected_final]
    external_inputs={str(Path(command['cwd'])/'external-configuration.txt') for command in commands}
    wrong_edits += [path for path in external_inputs if Path(path).read_text()!='delta']; mutable.update(external_inputs)
    changed=wrong_edits+[row['path'] for row in inputs if row['path'] not in mutable and sha(Path(row['path']))!=row['sha256']]
    (out/'completion.json').write_text(json.dumps(dict(lanes=results,inputsChanged=changed,intentionalEdits=sorted(mutable)),indent=2)+'\n')
    assert not changed and all(row['actualExit']==0 for row in results),results
    report=audit(dict(commands=commands),out)
    (out/'audit.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({key:value for key,value in report.items() if key!='records'}),flush=True)
if __name__=='__main__':main()
