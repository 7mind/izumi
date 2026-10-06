#!/usr/bin/env python3
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import importlib.util,json,re,shutil,subprocess,sys
from pathlib import Path
import xml.etree.ElementTree as ET
FIXTURE=Path(__file__).resolve().parent
sys.path.insert(0,str(FIXTURE))
from verify import prepare,sha
spec=importlib.util.spec_from_file_location('policy',FIXTURE/'verify-policy.py');policy=importlib.util.module_from_spec(spec);spec.loader.exec_module(policy)

CONTROLS='''
val editExternalConfiguration = inputKey[Unit]("Change untracked configuration input")
val editProviderImplementation = taskKey[Unit]("Change an implementation reached through the owner factory")
val editProviderConfiguration = taskKey[Unit]("Change the configuration snapshot source")
'''
SETTINGS='''
  libraryDependencies += "io.github.classgraph" % "classgraph" % "4.8.181" % Test,
  Test / scalacOptions ++= (if (scalaVersion.value.startsWith("3.")) Seq("-Yretain-trees", "-Xmax-inlines:64") else Seq.empty),
  libraryDependencies ++= Seq(
    "org.typelevel" % ("cats-effect_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % "3.7.1" % Test,
    ("dev.zio" % ("zio_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % "2.1.26" % Test).excludeAll(ExclusionRule(organization = "dev.zio", name = "izumi-reflect_" + candidatePlatform.value + "_" + scalaBinaryVersion.value)),
  ),
  libraryDependencySchemes ++= (if (candidatePlatform.value == "native0.5") Seq("org.scala-native" % ("test-interface_native0.5_" + scalaBinaryVersion.value) % "always") else Seq.empty),
  editExternalConfiguration := Def.uncached {
    val values = sbt.complete.DefaultParsers.spaceDelimited("snapshot").parsed
    require(values.size == 1 && Set("gamma", "delta").contains(values.head), "Unsupported external configuration snapshot")
    IO.write(fixtureRoot / "external-configuration.txt", values.head)
    println("SDK_DI_EDIT kind=external snapshot=" + values.head)
  },
  editProviderImplementation := Def.uncached {
    val source = fixtureRoot / "plugin-sources" / "ScannedPlugin.scala"
    val before = "private def implementationRevision: String = " + '"' + "one" + '"'
    val text = IO.read(source)
    require(text.contains(before), "Provider implementation edit precondition differs")
    IO.write(source, text.replace(before, before.replace("one", "two")))
    println("SDK_DI_EDIT kind=implementation revision=two")
  },
  editProviderConfiguration := Def.uncached {
    val source = fixtureRoot / "shared" / "FixturePlugin.scala"
    val before = "Json.fromString(" + '"' + "alpha" + '"' + ")"
    val text = IO.read(source)
    require(text.contains(before), "Provider configuration edit precondition differs")
    IO.write(source, text.replace(before, before.replace("alpha", "beta")))
    println("SDK_DI_EDIT kind=configuration snapshot=beta")
  },
'''

def dependency_revision(module, revision, dependency_module):
    namespace={'pom':'http://maven.apache.org/POM/4.0.0'}
    directory=Path.home()/'.ivy2/local/io.7mind.izumi'/module/revision/'poms'
    definitions=list(directory.glob('*.pom'));assert len(definitions)==1,directory
    revisions=[dependency.findtext('pom:version',namespaces=namespace) for dependency in ET.parse(definitions[0]).getroot().findall('pom:dependencies/pom:dependency',namespace) if dependency.findtext('pom:groupId',namespaces=namespace)=='io.7mind.izumi' and dependency.findtext('pom:artifactId',namespaces=namespace)==dependency_module]
    assert len(revisions)==1 and revisions[0],(module,dependency_module)
    return revisions[0]

def runtime_publication_inputs(module, version):
    namespace={'pom':'http://maven.apache.org/POM/4.0.0'}
    pending=[(module,version)];visited=set();inputs=[]
    while pending:
        name,revision=pending.pop()
        if (name,revision) in visited:continue
        visited.add((name,revision))
        directory=Path.home()/'.ivy2/local/io.7mind.izumi'/name/revision
        assert directory.is_dir(),directory
        inputs += [dict(path=str(path),sha256=sha(path)) for path in directory.rglob('*') if path.is_file()]
        definitions=list((directory/'poms').glob('*.pom'));assert len(definitions)==1,directory
        for dependency in ET.parse(definitions[0]).getroot().findall('pom:dependencies/pom:dependency',namespace):
            value=lambda field:dependency.findtext('pom:'+field,namespaces=namespace)
            if value('groupId')=='io.7mind.izumi' and value('scope')!='test':
                coordinate=(value('artifactId'),value('version'));assert all(coordinate),definitions[0]
                pending.append(coordinate)
    return inputs

def audit(command,out):
    build=Path(command['cwd']);log=(out/(command['scala']+'-'+command['platform']+'.log')).read_text();runs=set();records=[];resources=[]
    paths={1:'first',2:'second',3:'third'}
    for case in command['cases']:
        label=case['name'];segment=log.split('SDK_POLICY_BEGIN '+label+'\n',1)[1].split('SDK_POLICY_END '+label+'\n',1)[0]
        expected=Counter((suite,str(index)) for suite,indices in case['suites'].items() for index in indices)
        bodies=re.findall(r'SDK_DI_BODY suite=(\S+) test=(\d+) effect=(\S+) owner=(\S+) revision=(\S+) snapshot=(\S+) repo=(\S+)',segment)
        assert Counter((row[0],row[1]) for row in bodies)==expected,(label,'physical identities')
        acquired=re.findall(r'SDK_DI_ACQUIRE owner=(\S+) revision=(\S+) snapshot=(\S+) repo=(\S+)',segment);released=re.findall(r'SDK_DI_RELEASE owner=(\S+)',segment)
        assert Counter(row[0] for row in acquired)==Counter(released) and len(acquired)==case['resources'],(label,'resource lifetimes',acquired,released)
        assert not set(released).intersection(resources),(label,'resource reused by another command');resources+=released
        actual_effects={row[2] for row in bodies if row[2]!='plain'}
        for row in bodies:
            if row[2]=='plain':assert row[3:]==('plain','plain','plain','plain')
            else:assert row[3] in released and row[4:]==(case['revision'],case['snapshot'],case['repo']),(label,'DI body snapshot',row)
        if not case['unmemoized']:
            assert len(acquired)==len(actual_effects)
            for effect in actual_effects:assert len({row[3] for row in bodies if row[2]==effect})==1,(label,'sharing',effect)
        capture=build/'captures'/label;xml_cases=Counter()
        for path in (capture/'xml').glob('*.xml'):
            xml=ET.parse(path).getroot();assert not xml.findall('.//failure') and not xml.findall('.//error'),(label,path)
            xml_cases.update((xml.attrib['name'],item.attrib['name']) for item in xml.findall('.//testcase'))
        assert xml_cases==Counter((suite,'equal display name should '+paths[index]) for suite,indices in case['suites'].items() for index in indices),(label,'XML identities')
        streams=list((capture/'frames').glob('*.jsonl'));assert len(streams)==1,(label,'application count')
        messages=[json.loads(line)['message'] for line in streams[0].read_text().splitlines()];assert messages[-1]['kind']=='completed'
        outcome=messages[-1]['outcome'];assert not outcome['failures'] and not outcome['cancelled'];assert outcome['run'] not in runs;runs.add(outcome['run'])
        results=outcome['results'];assert all(row['status']=='succeeded' for row in results)
        assert Counter((row['id']['suite'],row['id']['path'][-1]) for row in results)==Counter((suite,paths[index]) for suite,indices in case['suites'].items() for index in indices)
        events=[row for row in messages if row['kind']=='event'];assert [int(row['sequence']) for row in events]==list(range(len(events)))
        identities=lambda values:Counter(json.dumps(value,sort_keys=True) for value in values)
        assert identities(row['event']['test'] for row in events if row['event']['kind']=='testStarted')==identities(row['event']['result']['id'] for row in events if row['event']['kind']=='testCompleted')==identities(row['id'] for row in results)
        assert segment.count('SDK_POLICY_SETUP')==segment.count('SDK_POLICY_CLEANUP')==1
        runtime=segment.split('SDK_POLICY_SETUP\n',1)[1].split('SDK_POLICY_CLEANUP\n',1)[0]
        assert re.findall(r'^SDK_DI_PLUGIN revision=(\S+)$',runtime,re.M)==[case['revision']],(label,'factory ownership')
        assert 'SDK_DI_ACQUIRE' not in segment.split('SDK_POLICY_SETUP\n',1)[0],(label,'discovery resource acquisition')
        if label.startswith('external-'):
            assert 'compiling ' not in segment,(label,'external input caused compilation')
            for suite in case['suites']:assert 'DISTAGE_CACHE_DECISION suite='+suite+' decision=rerun reason=untracked-input-closure' in segment,(label,suite,'cache decision')
        records.append(dict(case=label,tests=sum(expected.values()),resources=len(acquired),run=outcome['run'],files=[dict(path=str(path),sha256=sha(path)) for path in capture.rglob('*') if path.is_file()]))
    return dict(scala=command['scala'],platform=command['platform'],cases=records)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--repo-root',type=Path,required=True);parser.add_argument('--artifact-version',required=True);parser.add_argument('--production-host-version',required=True);parser.add_argument('--evidence-dir',type=Path,required=True);parser.add_argument('--scala-version',nargs='+',choices=['3.9.0','2.13.18'],required=True);args=parser.parse_args()
    args.repo_root=args.repo_root.resolve();args.host_threads='2';args.logical_suite_alias=False
    root=args.repo_root;template=root/'test-fixtures/target-runner-consumer/di'
    out=args.evidence_dir.resolve();out.mkdir();fixture=root/'test-fixtures/target-runner-consumer'
    paths=[path for path in fixture.rglob('*') if path.is_file() and path.suffix in ['.scala','.sbt','.properties','.py']]
    inputs=[dict(path=str(path),sha256=sha(path)) for path in paths]+[dict(path=str(root/'build.sbt'),sha256=sha(root/'build.sbt'))]+[dict(path=str(path),sha256=sha(path)) for path in template.glob('*.scala')]+[dict(path=str(Path(__file__).resolve()),sha256=sha(Path(__file__)))];commands=[]
    for compiler in args.scala_version:
        for platform in ['js','native']:
            suffix=('sjs1' if platform=='js' else 'native0.5')+'_'+('3' if compiler.startswith('3.') else '2.13')
            prepared_args=argparse.Namespace(**vars(args))
            prepared_args.artifact_version=dependency_revision('distage-testkit-runner_'+suffix,args.artifact_version,'distage-test-runner_'+suffix)
            command,prepared=prepare(prepared_args,compiler,platform,paths)
            command['argv']=[('-Dfixture.artifact-version='+args.artifact_version) if value.startswith('-Dfixture.artifact-version=') else value for value in command['argv']]
            build=Path(command['cwd']);(build/'project/ProductionInterruption.scala').unlink()
            for path in (build/'shared').glob('*.scala'):path.unlink()
            for path in template.glob('*.scala'):
                directory=build/('plugin-sources' if path.name=='ScannedPlugin.scala' else 'shared');directory.mkdir(exist_ok=True);shutil.copyfile(path,directory/path.name)
            external = build/'external-configuration.txt';external.write_text('')
            platform_source=build/('platform-'+platform)/'Platform.scala'
            text=platform_source.read_text()
            reader=('scala.scalajs.js.Dynamic.global.require("fs").readFileSync('+json.dumps(str(external))+', "utf8").asInstanceOf[String]' if platform=='js' else '{ val input = scala.io.Source.fromFile('+json.dumps(str(external))+'); try input.mkString finally input.close() }')
            method='  def overrideConfiguration(config: io.circe.JsonObject): io.circe.JsonObject = { val snapshot = '+reader+'.trim; if (snapshot.isEmpty) config else config.add("snapshot", io.circe.Json.fromString(snapshot)) }\n'
            platform_source.write_text(text.replace('object Platform {','object Platform {\n'+method))
            definition=build/'build.sbt';text=definition.read_text().replace('ProductionJsInterruptionPlugin','DistageTestkitJsPlugin').replace('ProductionNativeInterruptionPlugin','DistageTestkitNativePlugin').replace('distage-test-runner_','distage-testkit-runner_')
            text=text.replace('val common = Seq(',policy.CONTROLS+CONTROLS+'\nval common = Seq(\n'+policy.SETTINGS+SETTINGS)
            settings='''
val scannedPluginSettings = Seq(
  libraryDependencies += "io.7mind.izumi" % ("distage-testkit-runner_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % sys.props("fixture.artifact-version"),
  libraryDependencySchemes ++= (if (candidatePlatform.value == "native0.5") Seq("org.scala-native" % ("test-interface_native0.5_" + scalaBinaryVersion.value) % "always") else Seq.empty),
  Compile / unmanagedSourceDirectories := Seq(fixtureRoot / "plugin-sources"),
  Compile / scalacOptions ++= (if (scalaVersion.value.startsWith("3.")) Seq("-Yretain-trees", "-Xmax-inlines:64") else Seq("-Xsource:3"))
)
lazy val jsPlugins = project.in(file("plugin-js")).enablePlugins(ScalaJSPlugin).settings(scannedPluginSettings).settings(candidatePlatform := "sjs1")
lazy val nativePlugins = project.in(file("plugin-native")).enablePlugins(ScalaNativePlugin).settings(scannedPluginSettings).settings(candidatePlatform := "native0.5")
'''
            text=text.replace('lazy val js =',settings+'\nlazy val js =',1)
            text=text.replace('project.in(file("js"))','project.in(file("js")).dependsOn(jsPlugins)').replace('project.in(file("native"))','project.in(file("native")).dependsOn(nativePlugins)')
            definition.write_text(text)
            all_suites={name:[1,2,3] for name in ['candidate.Suite'+letter for letter in 'ABCDE']+['candidate.EffectCats','candidate.EffectZIO']};five={name:indices for name,indices in all_suites.items() if name.startswith('candidate.Suite')}
            test=dict(target='candidate-'+('sjs1' if platform=='js' else 'native0.5'),suite='candidate.SuiteC',path=['equal display name','should','first'],variant=None)
            selected=json.dumps(json.dumps(test,separators=(',',':')));axis=json.dumps(json.dumps(dict(axis='repo',value='prod'),separators=(',',':')))
            rows=[('full','testFull',all_suites,3,False,[],'one','alpha','dummy'),('five','testOnly *Suite*',five,1,False,[],'one','alpha','dummy'),('two','testOnly *SuiteC *SuiteD',{name:[1,2,3] for name in ['candidate.SuiteC','candidate.SuiteD']},1,False,[],'one','alpha','dummy'),('individual','testOnly *SuiteC -- --test-id '+selected,{'candidate.SuiteC':[1]},1,False,[],'one','alpha','dummy'),('after-partial','testQuick',all_suites,3,False,[],'one','alpha','dummy'),('axis','testOnly *SuiteC *EffectCats *EffectZIO -- --axis '+axis,{name:[1,2,3] for name in ['candidate.SuiteC','candidate.EffectCats','candidate.EffectZIO']},3,False,[],'one','alpha','prod'),('unmemoized','testOnly *SuiteC -- --memoization disabled',{'candidate.SuiteC':[1,2,3]},3,True,[],'one','alpha','dummy'),('implementation-test','test',all_suites,3,False,[platform+'/editProviderImplementation'],'two','alpha','dummy'),('implementation-quick','testQuick',all_suites,3,False,[],'two','alpha','dummy'),('configuration-test','test',all_suites,3,False,[platform+'/editProviderConfiguration'],'two','beta','dummy'),('configuration-quick','testQuick',all_suites,3,False,[],'two','beta','dummy'),('host-one','testFull',all_suites,3,False,['set Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))'],'two','beta','dummy')]
            rows += [('external-test','test',all_suites,3,False,[platform+'/editExternalConfiguration gamma'],'two','gamma','dummy'),('external-quick','testQuick',all_suites,3,False,[],'two','gamma','dummy'),('external-quick-change','testQuick',all_suites,3,False,[platform+'/editExternalConfiguration delta'],'two','delta','dummy'),('external-test-after-quick','test',all_suites,3,False,[],'two','delta','dummy')]
            requests=[];cases=[]
            for name,request,suites,resources,unmemoized,before,revision,snapshot,repo in rows:
                cases.append(dict(name=name,suites=suites,resources=resources,unmemoized=unmemoized,revision=revision,snapshot=snapshot,repo=repo));requests+=before+[platform+'/preparePolicy '+name,platform+'/'+request,platform+'/collectPolicy '+name]
            command['cases']=cases;command['argv']=command['argv'][:command['argv'].index(platform+'/testFull')]+requests
            inputs += [row for row in prepared if not Path(row['path']).is_relative_to(build)]
            module='distage-testkit-runner_'+('sjs1' if platform=='js' else 'native0.5')+'_'+('3' if compiler.startswith('3.') else '2.13')
            inputs += runtime_publication_inputs(module,args.artifact_version)
            inputs += [dict(path=str(path),sha256=sha(path)) for path in build.rglob('*') if path.is_file()]
            commands.append(command)
    (out/'commands.json').write_text(json.dumps(dict(inputs=inputs,commands=commands),indent=2)+'\n')
    def run(command):
        with (out/(command['scala']+'-'+command['platform']+'.log')).open('x') as log:child=subprocess.run(command['argv'],cwd=command['cwd'],stdout=log,stderr=subprocess.STDOUT,timeout=2400)
        row=dict(scala=command['scala'],platform=command['platform'],actualExit=child.returncode);print(json.dumps(row),flush=True);return row
    with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(run,commands))
    mutable={str(Path(command['cwd'])/'shared/FixturePlugin.scala') for command in commands}
    expected_final=(template/'FixturePlugin.scala').read_text().replace('private def implementationRevision: String = "one"','private def implementationRevision: String = "two"').replace('Json.fromString("alpha")','Json.fromString("beta")')
    wrong_edits=[path for path in mutable if Path(path).read_text()!=expected_final]
    plugin_inputs={str(Path(command['cwd'])/'plugin-sources/ScannedPlugin.scala') for command in commands}
    plugin_final=(template/'ScannedPlugin.scala').read_text().replace('private def implementationRevision: String = "one"','private def implementationRevision: String = "two"')
    wrong_edits += [path for path in plugin_inputs if Path(path).read_text()!=plugin_final];mutable.update(plugin_inputs)
    external_inputs={str(Path(command['cwd'])/'external-configuration.txt') for command in commands}
    wrong_edits += [path for path in external_inputs if Path(path).read_text()!='delta']; mutable.update(external_inputs)
    changed=wrong_edits+[row['path'] for row in inputs if row['path'] not in mutable and sha(Path(row['path']))!=row['sha256']]
    (out/'completion.json').write_text(json.dumps(dict(lanes=results,inputsChanged=changed,intentionalEdits=sorted(mutable)),indent=2)+'\n')
    assert not changed and all(row['actualExit']==0 for row in results),results
    lanes=[audit(command,out) for command in commands];report=dict(lanes=lanes,cases=sum(len(lane['cases']) for lane in lanes),tests=sum(case['tests'] for lane in lanes for case in lane['cases']),resources=sum(case['resources'] for lane in lanes for case in lane['cases']))
    (out/'audit.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='lanes'}),flush=True)
if __name__=='__main__':main()
