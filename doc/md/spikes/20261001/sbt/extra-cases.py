#!/usr/bin/env python3
from pathlib import Path
import argparse, time
from run_cases import run, BASE
p=argparse.ArgumentParser(); p.add_argument('version',type=int); v=p.parse_args().version
fixture=BASE/('fixture' if v==1 else 'fixture-sbt2')
nonce=str(time.time_ns())
(fixture/'di-only.txt').write_text('changed-before-stock-check-' + nonce + '\n')
run(v,'di-stock',['testQuick' if v==1 else 'test'],0,0,{})
if v==1:
    run(v,'arguments-stock',['testQuick spike.SuiteA -- --new-arg'],0,0,{})
    conservative = 'set Test / testQuick / testFilter := { val inherited = (Test / testQuick / testFilter).value; args => { val selected = Defaults.selectedFilter(args); Seq((name: String) => if (name.startsWith("spike.Suite")) selected.exists(_(name)) else inherited(args).exists(_(name))) } }'
    run(v,'di-conservative',[conservative,'testQuick'],15,1,{**dict.fromkeys(['spike.Suite'+c for c in 'ABCDE'],3)})
    run(v,'partial-conservative',[conservative,'testOnly spike.SuiteA -- --one','testQuick spike.SuiteA'],4,2,{'spike.SuiteA':3})
else:
    run(v,'arguments-new',['test spike.SuiteA -- --new-arg'],3,1,{'spike.SuiteA':3})
    run(v,'arguments-same',['test spike.SuiteA -- --new-arg'],0,0,{})
    extra = 'set Test / extraTestDigests := Def.uncached { (Test / extraTestDigests).value :+ sbt.util.Digest.sha256Hash(IO.readBytes(baseDirectory.value / "di-only.txt")) }'
    run(v,'di-extra-initial',[extra,'test'],15,1,{**dict.fromkeys(['spike.Suite'+c for c in 'ABCDE'],3),'spike.ForeignSuite':3},3)
    run(v,'di-extra-cached',[extra,'test'],0,0,{})
    (fixture/'di-only.txt').write_text('changed-after-extra-digest-' + nonce + '\n')
    run(v,'di-extra-changed',[extra,'test'],15,1,{**dict.fromkeys(['spike.Suite'+c for c in 'ABCDE'],3),'spike.ForeignSuite':3},3)
