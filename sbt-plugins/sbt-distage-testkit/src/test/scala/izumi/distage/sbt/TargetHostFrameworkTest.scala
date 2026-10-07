package izumi.distage.sbt

import izumi.distage.testkit.protocol.*

import sbt.testing.{Event, EventHandler, Fingerprint, Framework, Logger, NestedTestSelector, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, SuiteSelector, Task, TaskDef, TestSelector}

import java.net.Socket
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object TargetHostFrameworkTest {
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = "izumi.distage.testkit.runner.TestSuite"
    override def requireNoArgConstructor(): Boolean = true
  }

  def main(arguments: Array[String]): Unit = {
    require(arguments.isEmpty)
    checkUtfFrames()
    var failures = Vector.empty[String]
    for (threads <- Vector(1, 2); scenario <- Vector("success", "body-failure", "run-failure", "incomplete", "launch-failure", "bad-sequence", "cancel", "runner-failure", "tasks-failure", "logical-alias", "bad-owner", "cancel-before-start", "skip-before-start", "success-before-start", "bad-unstarted-owner", "large-frame", "truncated-frame", "bad-frame-owner", "duplicate-frame-part")) {
      try check(threads, scenario)
      catch { case NonFatal(cause) =>
        val message = s"threads=$threads scenario=$scenario cause=$cause"
        println("TARGET_HOST_CHECK_FAILURE " + message)
        failures :+= message
      }
    }
    require(failures.isEmpty, failures.mkString("; "))
    println("TARGET_HOST_FRAMEWORK_CHECK_OK scenarios=38")
  }

  private def check(threads: Int, scenario: String): Unit = {
    val largeFrame = Set("large-frame", "truncated-frame", "bad-frame-owner", "duplicate-frame-part").contains(scenario)
    val directory = Files.createTempDirectory("distage-target-host-")
    val launches = new AtomicInteger
    val runners = new AtomicInteger
    val completions = new AtomicInteger
    val started = new CountDownLatch(1)
    val platform = new Framework {
      override def name(): String = "fixture"
      override def fingerprints(): Array[Fingerprint] = Array(fingerprint)
      override def runner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader): Runner = {
        require(!args.contains(ForkReceiptArguments.EventDirectoryOption) && !args.contains(ForkReceiptArguments.HostDirectoryOption))
        require(!remoteArgs.contains(ForkReceiptArguments.ForkDirectoryOption))
        val portIndex = args.indexOf("--distage-control-port")
        require(portIndex >= 0)
        val port = args(portIndex + 1).toInt
        val capturedArgs = args.clone()
        val capturedRemoteArgs = remoteArgs.clone()
        val _ = runners.incrementAndGet()
        if (scenario == "runner-failure") throw new IllegalStateException("runner launch failed")
        new Runner {
          override def args(): Array[String] = capturedArgs.clone()
          override def remoteArgs(): Array[String] = capturedRemoteArgs.clone()
          override def done(): String = { val _ = completions.incrementAndGet(); "" }
          override def tasks(selected: Array[TaskDef]): Array[Task] = {
            if (scenario == "tasks-failure") throw new IllegalStateException("task construction failed")
            Array(new Task {
            override def taskDef(): TaskDef = selected.head
            override def tags(): Array[String] = Array.empty
            override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
              val _ = launches.incrementAndGet()
              if (scenario == "launch-failure") throw new IllegalStateException("launch failed")
              val run = RunId(UUID.randomUUID().toString)
              var sequence = 0L
              var eventOwner = selected.head.fullyQualifiedName()
              def frame(message: ProtocolMessage): Unit = {
                val encoded = SdkProtocolFrames.encode(ProtocolCodec.encode(message))
                val terminal = message.isInstanceOf[ProtocolMessage.Completed]
                val parts = if (terminal && scenario == "truncated-frame") encoded.dropRight(1)
                  else if (terminal && scenario == "duplicate-frame-part") encoded.take(1) ++ encoded
                  else encoded
                parts.zipWithIndex.foreach { case (part, index) =>
                  val partOwner = if (terminal && scenario == "bad-frame-owner" && index == 1) "another.Suite" else eventOwner
                  handler.handle(new Event {
                    override def fullyQualifiedName(): String = partOwner
                    override def fingerprint(): Fingerprint = TargetHostFrameworkTest.fingerprint
                    override def selector(): Selector = new NestedTestSelector(part.selectorId, part.payload)
                    override def status(): Status = Status.Success
                    override def throwable(): OptionalThrowable = new OptionalThrowable
                    override def duration(): Long = 0L
                  })
                }
              }
              def emit(event: RunEvent): Unit = { frame(ProtocolMessage.Event(sequence, event)); sequence += 1 }
              val socket = if (scenario == "cancel" && launches.get() == 1) Some(new Socket("127.0.0.1", port)) else None
              try {
                emit(RunEvent.Started(run))
                started.countDown()
                socket.foreach { connection =>
                  val input = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream, java.nio.charset.StandardCharsets.UTF_8))
                  require(ProtocolCodec.decode(input.readLine()) == Right(ProtocolMessage.Cancel(run)), "Cancellation did not reach target input")
                }
                val results = selected.toVector.flatMap { definition =>
                  (1 to 3).map { index =>
                    val failure = if (scenario == "body-failure" && definition == selected.last && index == 3) Some(error(FailurePhase.Test, "body failed")) else None
                    val suite = (if (Set("logical-alias", "cancel-before-start", "skip-before-start").contains(scenario)) "logical:" else "") + definition.fullyQualifiedName()
                    val status = scenario match {
                      case "cancel-before-start" | "bad-unstarted-owner" => TestStatus.Cancelled
                      case "skip-before-start" => TestStatus.Skipped
                      case _ => if (failure.isDefined) TestStatus.Failed else TestStatus.Succeeded
                    }
                    TestResult(TestId(BuildTargetId("fixture"), SuiteId(suite), Vector("same", index.toString) ++ (if (largeFrame) Vector("😀" * 750) else Vector.empty), None), status, failure, 1000000L)
                  }
                }
                results.reverse.foreach { result =>
                  eventOwner = if (Set("bad-owner", "bad-unstarted-owner").contains(scenario)) "fixture.Unselected" else result.id.suite.value.stripPrefix("logical:")
                  if (!Set("cancel-before-start", "skip-before-start", "success-before-start", "bad-unstarted-owner").contains(scenario)) emit(RunEvent.TestStarted(run, result.id))
                  emit(RunEvent.TestCompleted(run, result))
                }
                if (scenario != "incomplete") {
                  if (scenario == "bad-sequence") sequence += 1
                  val failures = if (scenario == "run-failure") Vector(error(FailurePhase.Finalization, "release failed")) else Vector.empty
                  val outcome = RunOutcome(run, results, failures, socket.isDefined)
                  emit(RunEvent.Finished(run, outcome))
                  frame(ProtocolMessage.Completed(outcome))
                }
              } finally socket.foreach(_.close())
              Array.empty
            }
            })
          }
        }
      }
    }
    val args = Array(ForkReceiptArguments.EventDirectoryOption, directory.toString, ForkReceiptArguments.HostDirectoryOption, directory.toString, ForkReceiptArguments.CommandCompletionOption)
    val runner = new TargetHostFramework(platform).runner(args, Array(ForkReceiptArguments.ForkDirectoryOption, directory.toString), getClass.getClassLoader)
    val selected = (1 to 5).map(index => new TaskDef("fixture.Suite" + index, fingerprint, false, Array(new SuiteSelector))).toArray
    var observed = Vector.empty[Event]
    val handler = new EventHandler { override def handle(event: Event): Unit = synchronized { observed :+= event } }
    def execute(tasks: Array[Task]): Unit = {
      val pool = Executors.newFixedThreadPool(threads)
      try {
        val jobs = tasks.map(task => pool.submit(new Callable[Unit] { override def call(): Unit = { require(task.execute(handler, Array.empty).isEmpty) } }))
        jobs.foreach(_.get(10, TimeUnit.SECONDS))
      } finally { pool.shutdownNow(); require(pool.awaitTermination(10, TimeUnit.SECONDS)) }
    }
    val tasks = runner.tasks(selected)
    require(tasks.map(_.taskDef().fullyQualifiedName()).toVector == selected.map(_.fullyQualifiedName()).toVector)
    if (scenario == "cancel") {
      // Interrupt the suite's actual execution thread, not the pool owner.
      val direct = new Thread(() => { val _ = tasks.head.execute(handler, Array.empty) }, "fixture-cancel-suite")
      direct.start()
      require(started.await(10, TimeUnit.SECONDS))
      direct.interrupt()
      direct.join(10000L)
      require(!direct.isAlive, "Cancellation did not complete")
      execute(tasks.tail)
    } else execute(tasks)
    val earlyFailure = Set("runner-failure", "tasks-failure").contains(scenario)
    require(launches.get() == (if (earlyFailure) 0 else 1), "Selected suite tasks relaunched the aggregate")
    if (Set("cancel-before-start", "skip-before-start").contains(scenario)) {
      val causes = observed.filter(_.status() == Status.Error).map(_.throwable().get().getMessage)
      require(causes.isEmpty, scenario + ": valid unstarted completion rejected: " + causes.mkString("; "))
      val status = if (scenario == "cancel-before-start") Status.Canceled else Status.Skipped
      require(observed.size == 15 && observed.forall(_.status() == status), scenario + ": unstarted completion projection")
    }
    val bodyCount = if (Set("launch-failure", "bad-owner", "success-before-start", "bad-unstarted-owner").contains(scenario) || earlyFailure) 0 else 15
    require(observed.count(_.selector().isInstanceOf[TestSelector]) == bodyCount, scenario + ": body event count")
    val groupFailure = !Set("success", "body-failure", "logical-alias", "cancel-before-start", "skip-before-start", "large-frame").contains(scenario)
    require(observed.count(_.status() == Status.Error) == (if (groupFailure) 5 else 0), scenario + ": suite error count")
    require(observed.count(_.status() == Status.Failure) == (if (scenario == "body-failure") 1 else 0))
    selected.foreach(definition => require(observed.filter(_.selector().isInstanceOf[TestSelector]).count(_.fullyQualifiedName() == definition.fullyQualifiedName()) == (if (bodyCount == 0) 0 else 3)))
    if (scenario == "cancel") {
      observed = Vector.empty
      execute(runner.tasks(selected))
      require(observed.size == 15 && observed.forall(_.status() == Status.Success), "Same-runner recovery failed")
    }
    require(runner.done().isEmpty && runners.get() - int(scenario == "runner-failure") == completions.get(), "Platform runners did not complete")
    val stream = Files.list(directory)
    val channels = try stream.iterator().asScala.toVector finally stream.close()
    require(channels.size == (if (scenario == "launch-failure" || earlyFailure) 0 else if (scenario == "cancel") 2 else 1))
    channels.foreach(path => { val _ = Files.deleteIfExists(path) })
    Files.delete(directory)
  }

  private def checkUtfFrames(): Unit = {
    val maximumUtfBytes = SdkProtocolFrames.MaxModifiedUtfBytes
    val formerlyFailingFrame = "x" * 74917
    val original = new java.io.DataOutputStream(new java.io.ByteArrayOutputStream)
    val rejected = try { original.writeUTF(formerlyFailingFrame); false } catch { case _: java.io.UTFDataFormatException => true }
    require(rejected, "The original oversized SDK selector must reproduce writeUTF failure")
    Vector(formerlyFailingFrame, "😀\u0000" * SdkProtocolFrames.MaxPartCharacters, "x" * ProtocolCodec.MaxFrameCharacters).foreach { value =>
      val decoder = new SdkProtocolFrames.Decoder
      val restored = SdkProtocolFrames.encode(value).flatMap { part =>
        val bytes = new java.io.ByteArrayOutputStream
        val output = new java.io.DataOutputStream(bytes)
        output.writeUTF(part.payload)
        require(bytes.size() - java.lang.Short.BYTES <= maximumUtfBytes)
        val input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray))
        decoder.accept("fixture.Suite", part.copy(payload = input.readUTF()))
      }
      require(restored == Vector(value), "Modified UTF-8 serialization changed a fragmented SDK frame")
      decoder.requireComplete()
    }
    println("TARGET_HOST_UTF_CHECK_OK frames=3")
  }

  private def error(phase: FailurePhase, message: String): Failure = Failure(phase, "fixture.Failure", message, Vector.empty, Vector.empty, None, Vector.empty, Vector.empty)
  private def int(value: Boolean): Int = if (value) 1 else 0
}
