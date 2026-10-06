package izumi.distage.sbt

import izumi.distage.testkit.protocol.{FileForkReceiptStore, ForkProcessId, ForkReceiptArguments, ForkReceiptCounts, ForkReceiptSuite, ForkReceiptSummary}

import sbt._
import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, SubclassFingerprint, SuiteSelector}

import java.nio.file.{Files, Paths}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

object HostReceiptTest {
  private val name = HostSuiteName("fixture.OwnedSuite")
  private val empty = Tests.Output(TestResult.Passed, Map.empty, Nil)

  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Receipt checks require a new owned fixture directory")
    val parent = Paths.get(arguments.head).toAbsolutePath
    require(!Files.exists(parent), "Receipt fixture directory must be new")
    val _ = Files.createDirectories(parent)
    def owner(): HostReceiptOwner = new HostReceiptOwner(() => FileForkReceiptStore.create(parent), ForkProcessId(ProcessHandle.current().pid()))
    check("reject a selected suite omitted by the target") {
      val receipt = new HostReceipt
      receipt.configure(Set(name))
      receipt.expect(name)
      rejects(classOf[MessageOnlyException])(receipt.verify(empty))
    }

    check("retain completion validation when the result logger is replaced") {
      val omitted = new HostReceipt
      omitted.expect(name)
      omitted.configure(Set(name)).doComplete(TestResult.Passed)
      rejects(classOf[MessageOnlyException])(omitted.verifyCompletion())
      val complete = new HostReceipt
      val listener = complete.configure(Set(name))
      complete.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Success))))
      listener.endGroup(name.value, TestResult.Passed)
      listener.doComplete(TestResult.Passed)
      complete.verifyCompletion()
    }

    check("reject a passed SDK completion after a received error") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      receipt.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Error))))
      listener.endGroup(name.value, TestResult.Error)
      listener.doComplete(TestResult.Passed)
      rejects(classOf[MessageOnlyException])(receipt.verifyCompletion())
    }

    check("reject success events without group completion") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      receipt.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Success))))
      rejects(classOf[MessageOnlyException])(receipt.verify(output(new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0))))
    }

    check("reconcile each status without treating equal totals as equal outcomes") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      receipt.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(Status.Success, Status.Failure, Status.Error, Status.Skipped, Status.Ignored, Status.Canceled, Status.Pending).map(event(name, _))))
      listener.endGroup(name.value, TestResult.Error)
      receipt.verify(output(new SuiteResult(TestResult.Error, 1, 1, 1, 1, 1, 1, 1)))

      val alias = new HostReceipt
      val aliasListener = alias.configure(Set(name))
      alias.expect(name)
      aliasListener.startGroup(name.value)
      aliasListener.testEvent(TestEvent(Seq(event(name, Status.Success))))
      aliasListener.endGroup(name.value, TestResult.Passed)
      rejects(classOf[MessageOnlyException])(alias.verify(output(new SuiteResult(TestResult.Error, 0, 0, 1, 0, 0, 0, 0))))
    }

    check("preserve a zero-event group failure") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      receipt.expect(name)
      listener.startGroup(name.value)
      listener.endGroup(name.value, new IllegalStateException("group failure"))
      receipt.verify(output(SuiteResult.Error))
    }

    check("accept empty selection and completed zero-event suites") {
      val receipt = new HostReceipt
      receipt.configure(Set(name))
      receipt.verify(empty)
      val zero = new HostReceipt
      val listener = zero.configure(Set(name))
      zero.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Nil))
      listener.endGroup(name.value, TestResult.Passed)
      zero.verify(output(new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)))
      rejects(classOf[IllegalArgumentException])(listener.startGroup(name.value))
    }

    check("leave foreign framework results unchanged") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      val foreign = HostSuiteName("fixture.ForeignSuite")
      listener.startGroup(foreign.value)
      listener.testEvent(TestEvent(Seq(event(foreign, Status.Error))))
      listener.endGroup(foreign.value, TestResult.Error)
      receipt.verify(Tests.Output(TestResult.Error, Map(foreign.value -> new SuiteResult(TestResult.Error, 0, 0, 1, 0, 0, 0, 0)), Nil))
    }

    check("reject truncated SDK output after foreign framework delivery") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      val foreign = HostSuiteName("fixture.ForeignSuite")
      receipt.expect(name)
      Seq(name, foreign).foreach { suite =>
        listener.startGroup(suite.value)
        listener.testEvent(TestEvent(Seq(event(suite, Status.Success))))
        listener.endGroup(suite.value, TestResult.Passed)
      }
      listener.doComplete(TestResult.Passed)
      rejects(classOf[MessageOnlyException])(receipt.verify(output(new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0))))
    }

    check("validate foreign completion when the result logger is replaced") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set.empty)
      val foreign = HostSuiteName("fixture.ForeignSuite")
      listener.startGroup(foreign.value)
      listener.testEvent(TestEvent(Seq(event(foreign, Status.Success))))
      listener.doComplete(TestResult.Passed)
      rejects(classOf[MessageOnlyException])(receipt.verifyCompletion())
    }

    check("preserve foreign event identities that differ from their group") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set.empty)
      val foreign = HostSuiteName("fixture.ForeignSuite")
      listener.startGroup(foreign.value)
      listener.testEvent(TestEvent(Seq(event(HostSuiteName("fixture.NestedSuite"), Status.Success))))
      listener.endGroup(foreign.value, TestResult.Passed)
      receipt.verify(Tests.Output(TestResult.Passed, Map(foreign.value -> new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0)), Nil))
    }

    check("reject foreign per-group status swaps with equal aggregate counts") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set.empty)
      val left = HostSuiteName("fixture.Left")
      val right = HostSuiteName("fixture.Right")
      Seq(left -> Status.Success, right -> Status.Failure).foreach { case (suite, status) =>
        listener.startGroup(suite.value)
        listener.testEvent(TestEvent(Seq(event(suite, status))))
        listener.endGroup(suite.value, if (status == Status.Success) TestResult.Passed else TestResult.Failed)
      }
      val swapped = Tests.Output(TestResult.Failed, Map(left.value -> new SuiteResult(TestResult.Failed, 0, 1, 0, 0, 0, 0, 0), right.value -> new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0)), Nil)
      rejects(classOf[MessageOnlyException])(receipt.verify(swapped))
    }

    check("merge repeated owned and foreign groups without changing complete SDK output") {
      Seq(true, false).foreach { owned =>
        val receipt = new HostReceipt
        val listener = receipt.configure(if (owned) Set(name) else Set.empty)
        if (owned) receipt.expect(name)
        Seq(Status.Success, Status.Error).foreach { status =>
          listener.startGroup(name.value)
          listener.testEvent(TestEvent(Seq(event(name, status))))
          listener.endGroup(name.value, if (status == Status.Success) TestResult.Passed else TestResult.Error)
        }
        val replaced = output(new SuiteResult(TestResult.Error, 0, 0, 1, 0, 0, 0, 0))
        val merged = receipt.normalise(replaced)
        require(HostSuiteCounts.from(merged.events(name.value)) == HostSuiteCounts(TestResult.Error, 1, 0, 1, 0, 0, 0, 0), "Repeated group counts were lost")
        require(receipt.normalise(merged) eq merged, "Already merged SDK output changed")
        receipt.verify(merged)
      }
    }

    check("retain a newer admission when an earlier task aborts") {
      val current = owner()
      val first = current.enter()
      rejects(classOf[IllegalArgumentException]) { val _ = current.enter(); () }
      current.consume(empty)
      val second = current.enter()
      require(first.store.directory != second.store.directory, "New admission reused an earlier directory")
      current.abort(first)
      assert(current.receipt eq second.receipt)
      require(!Files.exists(first.store.directory) && Files.isDirectory(second.store.directory), "Earlier cleanup affected a newer admission")
      current.consume(empty)
      current.abort(second)
      rejects(classOf[IllegalStateException]) { val _ = current.receipt; () }
    }

    check("close abandoned command generations after result consumption and preserve cancellation") {
      val first = owner()
      val consumed = first.enter()
      first.consume(empty)
      val active = first.enter()
      val second = owner()
      val other = second.enter()
      val cancellation = new InterruptedException("cancelled command")
      var callbacks = 0
      val inherited = new ExecuteProgressAdapter(ExecuteProgress.empty) {
        override def afterCommand(command: String, result: Either[Throwable, State]): Unit = {
          require(command == "testOnly fixture.*" && result == Left(cancellation), "Command callback changed")
          callbacks += 1
        }
      }
      val completion = new HostCommandCompletion(inherited, Seq(first, second))
      completion.afterCommand("testOnly fixture.*", Left(cancellation))
      require(callbacks == 1 && cancellation.getSuppressed.length == 1, "Command failure or inherited callback was lost")
      require(cancellation.getSuppressed.head.getSuppressed.length == 1, "Another owner's incomplete command was lost")
      require(Seq(consumed, active, other).forall(generation => !Files.exists(generation.store.directory)), "Abandoned command storage survived")
      Seq(first, second).foreach { current =>
        rejects(classOf[IllegalStateException]) { val _ = current.receipt; () }
        current.finishCommand()
        val fresh = current.enter()
        require(Seq(consumed, active, other).forall(_.store.directory != fresh.store.directory), "Recovery reused abandoned command storage")
        current.abort(fresh)
      }
    }

    check("distinguish owned task interruption from ordinary cancellation and foreign interruption") {
      val receipt = new HostReceipt
      val listener = receipt.configure(Set(name))
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Canceled))))
      require(!receipt.isInterrupted, "A cancelled outcome was mistaken for host task interruption")
      listener.testEvent(TestEvent(Seq(event(HostSuiteName("fixture.ForeignSuite"), Status.Error, new OptionalThrowable(new InterruptedException("foreign"))))))
      require(!receipt.isInterrupted, "Foreign task interruption changed distage completion")
      listener.testEvent(TestEvent(Seq(event(name, Status.Error, new OptionalThrowable(new InterruptedException("owned"))))))
      require(receipt.isInterrupted, "Owned task interruption was not retained")
      listener.endGroup(name.value, TestResult.Error)
      receipt.close()
    }

    Vector(false, true).foreach { interrupted =>
      check("join already executing SDK work only after owned interruption=" + interrupted) {
        val current = owner()
        val generation = current.enter()
        val listener = generation.receipt.configure(Set(name))
        listener.startGroup(name.value)
        val throwable = if (interrupted) new OptionalThrowable(new InterruptedException("owned")) else new OptionalThrowable
        listener.testEvent(TestEvent(Seq(event(name, Status.Canceled, throwable))))
        listener.endGroup(name.value, TestResult.Error)
        val completion = new HostCommandCompletion(new ExecuteProgressAdapter(ExecuteProgress.empty), Seq(current))
        val first = sbt.std.TaskExtra.task(())
        val second = sbt.std.TaskExtra.task(())
        completion.beforeWork(first)
        completion.beforeWork(second)
        val entered = new CountDownLatch(1)
        val finished = new CountDownLatch(1)
        val failure = new AtomicReference[Throwable]()
        val worker = new Thread(() => {
          entered.countDown()
          try completion.afterWork(first, Right(Result.Value(())))
          catch { case cause: Throwable => failure.set(cause) }
          finally finished.countDown()
        }, "host-interrupted-work")
        val WaitSeconds = 5L
        val HoldMillis = 100L
        try {
          worker.start()
          require(entered.await(WaitSeconds, TimeUnit.SECONDS), "SDK completion worker did not enter")
          val completed = finished.await(if (interrupted) HoldMillis else TimeUnit.SECONDS.toMillis(WaitSeconds), TimeUnit.MILLISECONDS)
          require(completed != interrupted, "SDK work completion did not respect the interruption boundary")
        } finally {
          completion.afterWork(second, Right(Result.Value(())))
          worker.join(TimeUnit.SECONDS.toMillis(WaitSeconds))
          current.abort(generation)
        }
        require(!worker.isAlive && failure.get() == null, "SDK work was not joined cleanly: " + failure.get())
      }
    }

    check("close abandoned commands when an inherited command callback throws") {
      val current = owner()
      val generation = current.enter()
      val failure = new IllegalStateException("inherited command callback")
      val inherited = new ExecuteProgressAdapter(ExecuteProgress.empty) {
        override def afterCommand(command: String, result: Either[Throwable, State]): Unit = throw failure
      }
      val completion = new HostCommandCompletion(inherited, Seq(current))
      var observed = Option.empty[Throwable]
      try completion.afterCommand("test", Left(new InterruptedException("cancelled")))
      catch { case cause: Throwable => observed = Some(cause) }
      require(observed.contains(failure), "Inherited command callback failure was replaced")
      require(failure.getSuppressed.length == 1 && !Files.exists(generation.store.directory), "Callback failure prevented command cleanup")
      current.finishCommand()
    }

    check("rebind inherited selection observers to the current configuration") {
      val inheritedOwner = owner()
      val inherited = new HostSelectionObserver(arguments => Seq(candidate => arguments.contains(candidate)), Set(name), inheritedOwner)
      val current = owner()
      val generation = current.enter()
      val listener = current.receipt.configure(Set(name))
      val selection = new HostSelectionObserver(inherited, Set(name), current)(Seq(name.value))
      assert(!selection.head("fixture.ExcludedSuite"))
      assert(selection.head(name.value))
      listener.startGroup(name.value)
      listener.endGroup(name.value, TestResult.Passed)
      current.consume(output(new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)))
      current.abort(generation)
    }

    check("verify once through opaque inherited loggers without consuming another admission") {
      val earlier = owner()
      val previous = earlier.enter()
      val current = owner()
      val generation = current.enter()
      var forwarded = 0
      val delegate = new TestResultLogger {
        override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = {
          require(output eq empty, "Result logger changed the SDK output")
          require(taskName == "fixture", "Result logger changed the task identity")
          forwarded += 1
        }
      }
      val inherited = new HostResultLogger(delegate, earlier)
      val opaque = new TestResultLogger {
        override def run(log: sbt.util.Logger, output: Tests.Output, taskName: String): Unit = inherited.run(log, output, taskName)
      }
      try {
        HostReceiptPolicy.logger(opaque, current).run(sbt.util.Logger.Null, empty, "fixture")
        require(forwarded == 1, "Inherited logger did not run exactly once")
        require(earlier.receipt eq previous.receipt, "Inherited logger consumed another command's receipt")
        rejects(classOf[IllegalStateException]) { val _ = current.receipt; () }
        earlier.consume(empty)
      } finally {
        current.abort(generation)
        earlier.abort(previous)
      }
    }

    check("publish exact fork receipts only after completed host groups") {
      val current = owner()
      val generation = current.enter()
      val reader = FileForkReceiptStore.open(generation.store.directory)
      val listener = new HostForkReceiptListener(current.receipt.configure(Set(name)), current.receipt, generation.store)
      val suite = ForkReceiptSuite(name.value)
      current.receipt.expect(name)
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Success))))
      assert(reader.received(suite).isEmpty)
      listener.endGroup(name.value, TestResult.Passed)
      assert(reader.received(suite).contains(ForkReceiptSummary(1, ForkReceiptCounts(1, 0, 0, 0, 0, 0, 0))))
      listener.startGroup(name.value)
      listener.testEvent(TestEvent(Seq(event(name, Status.Error))))
      listener.endGroup(name.value, TestResult.Error)
      assert(reader.received(suite).contains(ForkReceiptSummary(2, ForkReceiptCounts(1, 0, 1, 0, 0, 0, 0))))
      listener.startGroup("fixture.ForeignSuite")
      listener.endGroup("fixture.ForeignSuite", TestResult.Passed)
      assert(reader.received(ForkReceiptSuite("fixture.ForeignSuite")).isEmpty)
      current.consume(Tests.Output(TestResult.Error, Map(name.value -> new SuiteResult(TestResult.Error, 1, 0, 1, 0, 0, 0, 0), "fixture.ForeignSuite" -> new SuiteResult(TestResult.Passed, 0, 0, 0, 0, 0, 0, 0)), Nil))
      current.abort(generation)
      require(!Files.exists(generation.store.directory), "Owned fork directory survived cleanup")
    }

    check("replace inherited fork options and listeners with current ownership") {
      val first = owner()
      val previous = first.enter()
      val second = owner()
      val current = second.enter()
      val earlier = new HostForkReceiptListener(first.receipt.configure(Set.empty), first.receipt, previous.store)
      val foreign = new TestsListener {
        override def doInit(): Unit = ()
        override def startGroup(name: String): Unit = ()
        override def testEvent(event: TestEvent): Unit = ()
        override def endGroup(name: String, cause: Throwable): Unit = ()
        override def endGroup(name: String, result: TestResult): Unit = ()
        override def doComplete(result: TestResult): Unit = ()
      }
      val inherited = Tests.Execution(Seq(Tests.Listeners(Seq(earlier, foreign)), Tests.Argument(DistageHostPolicy.framework, ForkReceiptArguments.HostDirectoryOption, previous.store.directory.toString)), true, Seq.empty)
      val execution = HostReceiptPolicy.execution(inherited, Seq.empty, second, full = false, HostJUnitFileFormat.Standard)
      val listeners = execution.options.collect { case Tests.Listeners(values) => values }.flatten
      require(listeners.count(_.isInstanceOf[HostForkReceiptListener]) == 1 && listeners.contains(foreign) && !listeners.contains(earlier), "Inherited listeners were not rebound")
      val directories = execution.options.collect { case Tests.Argument(Some(framework), values) if framework == DistageHostPolicy.framework => values }
      require(directories == Seq(Seq(ForkReceiptArguments.HostDirectoryOption, current.store.directory.toString, ForkReceiptArguments.CommandCompletionOption)), "Inherited receipt directory was retained")
      first.abort(previous); second.abort(current)
    }

    check("retain a fork publication failure through result and completion verification") {
      Seq(false, true).foreach { completionOnly =>
        val current = owner()
        val generation = current.enter()
        val listener = new HostForkReceiptListener(current.receipt.configure(Set(name)), current.receipt, generation.store)
        current.receipt.expect(name)
        listener.startGroup(name.value)
        listener.testEvent(TestEvent(Seq(event(name, Status.Success))))
        generation.store.close()
        var publicationFailure = Option.empty[Throwable]
        try listener.endGroup(name.value, TestResult.Passed)
        catch { case scala.util.control.NonFatal(cause) => publicationFailure = Some(cause) }
        require(publicationFailure.exists(cause => cause.isInstanceOf[IllegalArgumentException] && cause.getMessage.contains("Fork receipt directory is closed")), "Publication fault was not reproduced: " + publicationFailure)
        listener.doComplete(TestResult.Passed)
        var verificationFailure = Option.empty[Throwable]
        try {
          if (completionOnly) current.receipt.verifyCompletion()
          else current.consume(output(new SuiteResult(TestResult.Passed, 1, 0, 0, 0, 0, 0, 0)))
        } catch { case scala.util.control.NonFatal(cause) => verificationFailure = Some(cause) }
        finally current.abort(generation)
        require(verificationFailure.exists(cause => cause.isInstanceOf[MessageOnlyException] && (cause.getCause eq publicationFailure.get)), "HOST_PUBLICATION_FALSE_SUCCESS: verification did not retain the publication cause: " + verificationFailure)
      }
    }

    check("retry failed owned cleanup without clearing a newer admission") {
      val current = owner()
      val previous = current.enter()
      val blocker = Files.createDirectory(previous.store.directory.resolve("fixture-blocker"))
      rejects(classOf[IllegalArgumentException])(current.abort(previous))
      rejects(classOf[IllegalStateException]) { val _ = current.receipt; () }
      val generation = current.enter()
      try {
        Files.delete(blocker)
        current.abort(previous)
        assert(current.receipt eq generation.receipt)
        require(!Files.exists(previous.store.directory) && Files.isDirectory(generation.store.directory), "Cleanup retry affected a newer admission")
        current.consume(empty)
      } finally current.abort(generation)
    }

    Files.delete(parent)
  }

  private def check(name: String)(body: => Unit): Unit = {
    body
    println("HOST_RECEIPT_CHECK_OK " + name)
  }

  private def rejects[A <: Throwable](expected: Class[A])(body: => Unit): Unit = {
    var observed = Option.empty[Throwable]
    try body catch { case scala.util.control.NonFatal(cause) => observed = Some(cause) }
    require(observed.exists(expected.isInstance), "Receipt counterexample did not produce " + expected.getName + ": " + observed)
  }

  private def output(counts: SuiteResult): Tests.Output = Tests.Output(counts.result, Map(name.value -> counts), Nil)

  private def event(suite: HostSuiteName, state: Status): Event = event(suite, state, new OptionalThrowable)

  private def event(suite: HostSuiteName, state: Status, cause: OptionalThrowable): Event = new Event {
    override def fullyQualifiedName(): String = suite.value
    override def fingerprint(): Fingerprint = new SubclassFingerprint {
      override def isModule(): Boolean = false
      override def superclassName(): String = "fixture.Spec"
      override def requireNoArgConstructor(): Boolean = true
    }
    override def selector(): Selector = new SuiteSelector
    override def status(): Status = state
    override def throwable(): OptionalThrowable = cause
    override def duration(): Long = 0L
  }
}
