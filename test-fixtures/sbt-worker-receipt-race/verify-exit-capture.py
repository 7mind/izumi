from pathlib import Path
import argparse
import base64
import hashlib
import json
import os
import shutil
import subprocess
import time

SOURCE = r'''package fixture;
public final class ExitMain {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "normal": case "abort": System.exit(0); break;
            case "early-zero": early(0); break;
            case "early-one": early(1); break;
            case "main-one": System.exit(1); break;
            case "runtime": Runtime.getRuntime().exit(0); break;
            case "halt": Runtime.getRuntime().halt(0); break;
            case "natural": break;
            case "competing":
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    Thread request = new Thread(() -> Runtime.getRuntime().exit(1));
                    request.setDaemon(true);
                    request.start();
                    try { Thread.sleep(100L); }
                    catch (InterruptedException cause) { throw new RuntimeException(cause); }
                }));
                System.exit(0); break;
            case "denied":
                System.setSecurityManager(new SecurityManager() {
                    @Override public void checkPermission(java.security.Permission permission) { }
                    @Override public void checkExit(int status) { throw new SecurityException("Denied exit request"); }
                });
                try { early(0); throw new AssertionError("Exit was not denied"); }
                catch (SecurityException expected) { System.setSecurityManager(null); }
                System.exit(0); break;
            default: throw new IllegalArgumentException(args[0]);
        }
    }
    private static void early(int status) { System.exit(status); }
}
'''

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--agent-jar',type=Path,required=True)
    parser.add_argument('--jdk17-home',type=Path,required=True)
    parser.add_argument('--jdk21-home',type=Path,required=True)
    parser.add_argument('--jdk25-home',type=Path,required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    args = parser.parse_args()
    out = args.evidence_dir.resolve(); out.mkdir()
    shutil.copy2(__file__,out/'driver.py')
    agent = out/'agent.jar'; shutil.copy2(args.agent_jar,agent)
    source = out/'src/fixture/ExitMain.java'; source.parent.mkdir(parents=True)
    source.write_text(SOURCE)
    classes = out/'classes'
    compile_argv = [str(args.jdk21_home/'bin/javac'),'--release','17','-d',str(classes),str(source)]
    with (out/'compile.log').open('x') as log:
        compile_exit = subprocess.run(compile_argv,stdout=log,stderr=subprocess.STDOUT).returncode
    (out/'compile-command.json').write_text(json.dumps(compile_argv,indent=2)+'\n')
    if compile_exit != 0: raise RuntimeError('Exit fixture compilation failed: inspect compile.log')
    results = []
    for version,jdk in [('17',args.jdk17_home),('21',args.jdk21_home),('25',args.jdk25_home)]:
        modes = ['normal','abort','early-zero','early-one','main-one','runtime','halt','natural','competing']
        if version != '25': modes.append('denied')
        for mode in modes:
            evidence = out/('jdk'+version+'-'+mode); evidence.mkdir()
            prefix = evidence/'worker'
            encoded = base64.urlsafe_b64encode(str(prefix).encode()).decode().rstrip('=')
            argv = [str(jdk/'bin/java'),'-javaagent:'+str(agent)+'='+encoded+':'+str(os.getpid()),'-cp',str(classes),'fixture.ExitMain',mode]
            if mode == 'denied': argv.insert(1,'-Djava.security.manager=allow')
            (evidence/'command.json').write_text(json.dumps(argv,indent=2)+'\n')
            held = False
            with (evidence/'run.log').open('x') as log:
                process = subprocess.Popen(argv,stdout=log,stderr=subprocess.STDOUT)
                try:
                    if mode in ['normal','abort','competing','denied']:
                        deadline = time.monotonic()+10
                        while not prefix.with_suffix('.shutdown').exists() and process.poll() is None and time.monotonic()<deadline:
                            time.sleep(0.02)
                        time.sleep(0.2)
                        held = process.poll() is None
                        prefix.with_suffix('.decision').write_text('abort' if mode == 'abort' else 'commit')
                    code = process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(); code = 'timeout'
            files = {path.name:path.read_text() for path in sorted(evidence.glob('worker.*'))}
            if mode in ['normal','abort','competing','denied']:
                acknowledgement = 'worker.aborted' if mode == 'abort' else 'worker.ready'
                passed = code == 0 and held and files.get('worker.exit') == '0\ttrue' and files.get('worker.entered') == str(process.pid) and files.get(acknowledgement) == str(process.pid) and files.get('worker.shutdown') == str(process.pid)
                if mode == 'competing': passed = passed and '1\tfalse' in [value for name,value in files.items() if name.startswith('worker.exit-thread-')]
            elif mode == 'halt':
                passed = code == 0 and 'worker.entered' in files and 'worker.shutdown' not in files
            else:
                expected = 1 if mode in ['early-one','main-one'] else 0
                passed = code == expected and 'worker.failed' in files and 'worker.ready' not in files and 'worker.aborted' not in files
            result = dict(jdk=version,mode=mode,actualExit=code,held=held,files=files,passed=passed)
            results.append(result)
            (evidence/'completion.json').write_text(json.dumps(result,indent=2)+'\n')
            print(json.dumps(result),flush=True)
    result = dict(exit=0 if all(row['passed'] for row in results) else 1,cases=results,agentSha256=hashlib.sha256(agent.read_bytes()).hexdigest(),scope='Published host-packaged JVM agent; exit origin/status, competing and denied requests, normal commit/abort acknowledgements on JDK17/21/25. This does not establish the entire SBT cancellation or reporting contract.')
    (out/'completion.json').write_text(json.dumps(result,indent=2)+'\n')
    raise SystemExit(result['exit'])

if __name__ == '__main__': main()
