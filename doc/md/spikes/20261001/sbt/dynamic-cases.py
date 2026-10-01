#!/usr/bin/env python3
from pathlib import Path
from run_cases import BASE, run
import argparse, time
p=argparse.ArgumentParser();p.add_argument('version',type=int);v=p.parse_args().version
fixture=BASE/('fixture' if v==1 else 'fixture-sbt2')
names=['spike.Suite'+c for c in 'ABCDE']
wiring=fixture/'src/test/java/spike/WiringOnly.java'
wiring.write_text('package spike; public final class WiringOnly { public static String value() { return \"implementation-one\"; } }\n')
run(v,'dynamic-baseline',['test' if v==1 else 'testFull'],15,1,{**dict.fromkeys(names,3),'spike.ForeignSuite':3},3)
wiring=fixture/'src/test/java/spike/WiringOnly.java'
source=wiring.read_text(); wiring.write_text(source.replace('implementation-one','implementation-two'))
run(v,'dynamic-stock',['testQuick' if v==1 else 'test'],0,0,{})
if v==1:
    policy='set Test / testQuick / testFilter := { val inherited = (Test / testQuick / testFilter).value; args => { val selected = Defaults.selectedFilter(args); Seq((name: String) => if (name.startsWith("spike.Suite")) selected.exists(_(name)) else inherited(args).exists(_(name))) } }'
    run(v,'dynamic-conservative',[policy,'testQuick'],15,1,dict.fromkeys(names,3))
else:
    # Each rerun must use a fresh explicit digest input, since SBT action successes persist.
    config=fixture/'di-only.txt'
    config.write_text(config.read_text() + 'dynamic-run=' + str(time.time_ns()) + '\n')
    policy='set Test / extraTestDigests := Def.uncached { val compiled = (Test / compile).value; (Test / extraTestDigests).value :+ sbt.util.Digest.sha256Hash(IO.readBytes((Test / classDirectory).value / "spike/WiringOnly.class") ++ IO.readBytes(baseDirectory.value / "di-only.txt")) }'
    run(v,'dynamic-digest-initial',[policy,'test'],15,1,{**dict.fromkeys(names,3),'spike.ForeignSuite':3},3)
    run(v,'dynamic-digest-cached',[policy,'test'],0,0,{})
    source=wiring.read_text(); wiring.write_text(source.replace('implementation-two','implementation-three'))
    run(v,'dynamic-digest-changed',[policy,'test'],15,1,{**dict.fromkeys(names,3),'spike.ForeignSuite':3},3)
