Problem: I am investigating rare occurences of failure to interrupt tests that run in 'Identity' effect type.

The reproduction script is in `./debug/interruption-loop.sh`, use it to reproduce the issue as in:

```bash
./debug/interruption-loop.sh 100
```

and

```bash
./debug/interruption-loop.sh 100 async
```

for async test.

The flow starts with InterruptionTestBlockingZIO_AllEffects in `InterruptionTest.scala` in distage-testkit-scalatest/.jvm

There are nested unsafeRuns in that test as per testkit architecture
-> zio blockingRuntime unsafeRun (defined in TestRuntime.scala)
-> zio RunnerToF unsafeRun (defined in .jvm / RunnerToF.scala)
-> QuasiIORunner unsafeRun (defined in .jvm QuasiIORunner)

There is also QuasiIORunner for Identity.

In .jvm @UnsafeRun2.scala there exist ZIORunner overrides `v_good` and `v_badRunOrFork`.
With v_good there are sometimes failures to interrupt in Identity.
With v_badRunOrFork there are also failures to interrupt inner ZIO tests as well.

There was an attempt to replicate failures to interrupt independently of testkit,
in file @ZIORunOrForkInterruptedFlagReproTest.scala - that file currently fails to replicate the problem.

Current goals:

1. **COMPLETED** Trace the execution path of InterruptionTestBlockingZIO_AllEffects
  - Write down into `INTERRUPTION.md` all information that may be relevant wrt the travel path of Java interruption signal, starting from Ctrl-C and whatever may cause failure to interrupt all tests.
  - You may not only read source code, but modify it and use debug prints to trace execution when it cannot be derived well from source code.
    - Use 'direnv exec . sbt testOnly' for running
  - You may also run the reproduction script and inspect output logs in /tmp/exchange to figure out the problem.
  - Write down maximum available info from your findings SUCH THAT WORK CAN CONTINUE IN A DIFFERENT AGENT from the information you record. Record everything relevant, including if necessary parts of this prompt and the overall
  - Git commit when done and mark this item as **COMPLETED**.

2. Once all relevant information is written down into `INTERRUPTION.md`, replicate the non-interruption issue in .jvm/.../ZIORunOrForkInterruptedFlagReproTest.scala
  - Use information written in `INTERRUPTION.md` to replicate the flow of interruption signal from InterruptionTestBlockingZIO_AllEffects to the repro test
  - If new findings arise, write them down into `INTERRUPTION.md`
  - you can use MiniBIOAsync itself, QuasiIORunner and everything necessary in fundamentals-bio/* in repro test, first priority is to make a reproduction independent of the testkit. It can depend on BIO/MiniBIO etc. Purifying it from BIO dependencies is a step that will come later.
  - Keep going until you reproduce the failure to interrupt for the Identity tests/workers/runners in repro test.
   - Git commit when reproduction succeeds and mark this item as **COMPLETED**.

3. If Identity failure to interrupt succeeds, replicate the non-interruption of ZIO tests/workers/runners when unsafeRunSync is defined via `v_badRunOrFork` in repro test
  - Git commit when reproduction succeeds and mark this item as **COMPLETED**.

4. Once both failures ARE REPRODUCED COMPREHENSIVELY - that is the tests capture these failures, not their absence. Work on purifying them from MiniBIOAsync / bio dependencies such that they only require ZIO (+scalatest itself)

/ultrathink


Addendum:

The plan has changed. Previous agent has uncovered that failures for both `v_good` and `v_badRunOrFork` had nothing to do with bugs in upstream ZIO runtime.
They were caused by a stupid typo in the test itself. However, the current impl of UnsafeRun2 uses `v_initial`. This one reproduces failures to interrupt in ZIO (not Identity) tests:

```
230484: n=23 second test was not interrupted for id=1:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
230486: n=23 second test was not interrupted for id=3:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
230488: n=23 second test was not interrupted for id=2:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
230490: n=23 second test was not interrupted for id=1:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
230492: n=23 second test was not interrupted for id=2:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
230494: n=23 second test was not interrupted for id=3:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
230496: n=23 second test was not interrupted for id=1:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
230498: n=23 second test was not interrupted for id=3:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
230500: n=23 second test was not interrupted for id=2:tagMonoIO=Tag[λ %0 → ZIO[-Any,+Throwable,+0]]
```
