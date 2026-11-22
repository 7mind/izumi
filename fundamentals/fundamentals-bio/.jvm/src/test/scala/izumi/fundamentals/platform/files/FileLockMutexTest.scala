package izumi.fundamentals.platform.files

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiTemporal}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import org.scalatest.Outcome
import org.scalatest.wordspec.FixtureAnyWordSpec

import java.io.{BufferedReader, File, IOException, InputStreamReader, PrintWriter}
import java.net.{URL, URLClassLoader}
import java.nio.channels.{FileLock, OverlappingFileLockException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.UUID
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, LinkedBlockingQueue, TimeUnit}
import java.util.jar.{Attributes, JarOutputStream, Manifest}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future, Promise, TimeoutException}
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

private[files] trait FileLockMutexFixture extends FixtureAnyWordSpec {
  override type FixtureParam = () => String

  override protected def withFixture(test: OneArgTest): Outcome = {
    val dir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "file-lock-test-")
    try withFixture(test.toNoArgTest(() => dir.getFileName.resolve(UUID.randomUUID().toString).toString))
    finally {
      Using.resource(Files.newDirectoryStream(dir))(_.forEach(path => Files.delete(path)))
      Files.delete(dir)
    }
  }

  protected def classpath: List[URL] = {
    val loaders = Iterator.iterate[ClassLoader](getClass.getClassLoader)(_.getParent).takeWhile(_ != null)
    val urls = loaders.collect { case loader: URLClassLoader => loader.getURLs.toList }.flatten
    (urls ++ System.getProperty("java.class.path").split(File.pathSeparator).iterator.map(p => Paths.get(p).toUri.toURL)).toList.distinct
  }

  protected def childJvm(main: String, name: String, args: String*): ProcessBuilder = {
    val dir = Paths.get(System.getProperty("java.io.tmpdir"), name).getParent
    val jar = Files.createTempFile(dir, "classpath-", ".jar")
    val manifest = new Manifest()
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0").discard()
    manifest.getMainAttributes.put(Attributes.Name.CLASS_PATH, classpath.map(_.toURI.toASCIIString).mkString(" ")).discard()
    Using.resource(new JarOutputStream(Files.newOutputStream(jar), manifest))(_ => ())
    val java = Paths.get(System.getProperty("java.home"), "bin", "java").toString
    new ProcessBuilder((List(java, "-Xmx128m", s"-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}", "-cp", jar.toString, main, name) ++ args).asJava)
  }
}

class FileLockMutexTest extends FileLockMutexFixture {
  private val retryWait = 10.millis
  private val watchdog = 10.seconds
  private val timer = QuasiTemporal.quasiTimerIdentity

  "FileLockMutex" should {
    "keep a lock exclusive until its lifecycle is released" in { mutexName =>
      val name = mutexName()
      val (valid, release) = mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).unsafeAllocate()
      try {
        assert(valid)
        val overlapping = mutex(name, 0, timer, (_, _) => ())(_ => false, _ => true).use(identity)
        assert(!overlapping)
      } finally release()
      assert(mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).use(identity))
    }

    "release a lock when its acquisition callback fails" in { mutexName =>
      val name = mutexName()
      val expected = new IllegalStateException("acquisition callback failed")
      val error = intercept[IllegalStateException] {
        mutex[Unit](name, 0, timer, (_, _) => ())(_ => fail("unexpected exhaustion"), _ => throw expected).use(identity)
      }
      assert(error eq expected)
      assert(mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).use(identity))
    }

    "release a lock when its use fails" in { mutexName =>
      val name = mutexName()
      val expected = new IllegalStateException("use failed")
      val error = intercept[IllegalStateException] {
        mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).use(_ => throw expected)
      }
      assert(error eq expected)
      assert(mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).use(identity))
    }

    "preserve the exact same-process retry budget including zero" in { mutexName =>
      List(0, 2).foreach { maximum =>
        val name = mutexName()
        val (_, release) = mutex(name, 0, timer, (_, _) => ())(_ => (), _ => ()).unsafeAllocate()
        val attempts = ArrayBuffer.empty[(Int, Int)]
        val failures = ArrayBuffer.empty[Int]
        var sleeps = 0
        val observedTimer = new QuasiTemporal[Identity] {
          override def sleep(duration: FiniteDuration): Unit = {
            assert(duration == retryWait)
            sleeps += 1
          }
        }
        try {
          val acquired = mutex(name, maximum, observedTimer, (attempt, limit) => (attempts += ((attempt, limit))).discard())(
            attempt => { (failures += attempt).discard(); false },
            _ => true,
          ).use(identity)
          assert(!acquired)
          assert(failures.toList == List(maximum))
          assert(attempts.toList == (1 to maximum).map(_ -> maximum).toList)
          assert(sleeps == maximum)
        } finally release()
      }
    }

    "wait for another process even when the same-process retry budget is zero" in { mutexName =>
      val name = mutexName()
      withChild(name, mutexName()) { child =>
        val started = Promise[Unit]()
        val acquired = start {
          mutex(name, 0, timer, (_, _) => fail("unexpected overlap retry"), started.success(()).discard())(
            _ => fail("external ownership exhausted the same-process budget"),
            _.isValid,
          ).use(identity)
        }
        awaitContention(acquired, started)
        child.command("RELEASE")
        assert(Await.result(acquired, watchdog))
      }
    }

    "allow independent requests in two processes without a false deadlock" in { mutexName =>
      val first = mutexName()
      val second = mutexName()
      withChild(second, first) { child =>
        val (_, release) = mutex(first, 0, timer, (_, _) => ())(_ => (), _ => ()).unsafeAllocate()
        val started = Promise[Unit]()
        val acquired = start {
          mutex(second, 0, timer, (_, _) => fail("unexpected overlap retry"), started.success(()).discard())(
            _ => fail("unexpected exhaustion"),
            _.isValid,
          ).use(identity)
        }
        try {
          awaitContention(acquired, started)
          child.command("WAIT")
          assert(child.awaitAny(Set("WAITING", "ERROR")) == "WAITING")
          val early = child.pollAny(Set("COMPLETE", "ERROR"), 1.second)
          assert(early.isEmpty, s"contender finished before release: $early")
        } finally release()
        child.command("RELEASE")
        assert(Await.result(acquired, watchdog))
        assert(child.awaitAny(Set("COMPLETE", "ERROR")) == "COMPLETE")
      }
    }

    "evaluate the existing-file callback by name and once per allocation" in { mutexName =>
      val name = mutexName()
      var existed = 0
      def allocate() = FileLockMutex.allocate[Identity, Boolean](name, retryWait, 0, (_, _) => (), existed += 1)(_ => false, _.isValid)
      val first = allocate()
      assert(existed == 0)
      val (valid, release) = first.unsafeAllocate()
      try {
        assert(valid && existed == 0)
        assert(!allocate().use(identity) && existed == 1)
      } finally release()
      assert(allocate().use(identity) && existed == 2)
    }

    "propagate an overlap thrown by the success callback" in { mutexName =>
      val expected = new OverlappingFileLockException
      val result = intercept[OverlappingFileLockException] {
        mutex[Unit](mutexName(), 0, timer, (_, _) => fail("unexpected retry"))(_ => fail("unexpected exhaustion"), _ => throw expected).use(identity)
      }
      assert(result eq expected)
    }

    "keep the lock file reachable when its creator exits" in { mutexName =>
      val name = mutexName()
      val file = Paths.get(System.getProperty("java.io.tmpdir"), s"$name.tmp")
      var release = Option.empty[() => Unit]
      try {
        withChild(name, mutexName()) { child =>
          child.command("RELEASE")
          assert(child.awaitAny(Set("RELEASED")) == "RELEASED")
          val (valid, unlock) = mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).unsafeAllocate()
          release = Some(unlock)
          assert(valid)
        }
        assert(Files.isRegularFile(file), "creator exit unlinked a live lock file")
      } finally release.foreach(_())
    }

    "interrupt a ZIO wait through BIO before its owner releases the lock" in { mutexName =>
      checkInterruptedWait(mutexName) { (name, ec, started, succeeded) =>
        val wait = FileLockMutex.withLocalMutex[zio.Task, Unit](name, retryWait, 0, (_, _) => zio.ZIO.unit, zio.ZIO.succeed(started()))(
          _ => zio.ZIO.fail(new AssertionError("unexpected exhaustion")),
          _ => zio.ZIO.succeed(succeeded()),
        )(QuasiIO.fromBIO[zio.IO], QuasiAsync.fromBIO[zio.IO], QuasiTemporal.fromBIO[zio.IO])
        val result = zio.Unsafe.unsafe { implicit unsafe => zio.Runtime.default.unsafe.runToFuture(wait.onExecutionContext(ec)) }
        () => result.cancel().map(exit => assert(exit.isInterrupted).discard())(ec)
      }
    }

    "cancel an external wait before its owner releases the lock" in { mutexName =>
      checkInterruptedWait(mutexName) { (name, ec, started, succeeded) =>
        val wait = FileLockMutex.withLocalMutex[IO, Unit](name, retryWait, 0, (_, _) => IO.unit, IO(started()))(
          _ => IO.raiseError(new AssertionError("unexpected exhaustion")),
          _ => IO(succeeded()),
        )
        val (_, cancelWait) = wait.evalOn(ec).unsafeToFutureCancelable()(IORuntime.global)
        cancelWait
      }
    }

    "propagate file errors without invoking contention or success callbacks" in { mutexName =>
      var exhausted = false
      var succeeded = false
      intercept[IOException] {
        mutex[Unit](s"${mutexName()}/missing", 0, timer, (_, _) => fail("unexpected retry"))(
          _ => { exhausted = true; () },
          _ => { succeeded = true; () },
        ).use(identity)
      }.discard()
      assert(!exhausted)
      assert(!succeeded)
    }
  }

  private def checkInterruptedWait(mutexName: () => String)(
    startWaiter: (String, ExecutionContext, () => Unit, () => Unit) => (() => Future[Unit])
  ): Unit = {
    val name = mutexName()
    withChild(name, mutexName()) { child =>
      val ec = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
      val started = Promise[Unit]()
      val succeeded = Promise[Unit]()
      try {
        val cancelWait = startWaiter(name, ec, () => ec.execute(() => started.success(()).discard()), () => succeeded.success(()).discard())
        Await.result(started.future, watchdog)
        val canceled = cancelWait()
        try Await.result(canceled, watchdog)
        finally {
          child.command("RELEASE")
          Await.ready(canceled, watchdog).discard()
        }
        assert(child.awaitAny(Set("RELEASED")) == "RELEASED")
        assert(mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).use(identity))
        assert(!succeeded.isCompleted).discard()
      } finally ec.shutdown()
    }
  }

  private def mutex[A](
    name: String,
    maximum: Int,
    temporal: QuasiTemporal[Identity],
    attemptLog: (Int, Int) => Unit,
    onExisting: => Unit = (),
  )(failure: Int => A,
    success: FileLock => A,
  ): Lifecycle[Identity, A] = FileLockMutex.allocate[Identity, A](name, retryWait, maximum, attemptLog, onExisting)(
    failure,
    success,
  )(QuasiIO.quasiIOIdentity, QuasiAsync.quasiAsyncIdentity, temporal)

  private def start[A](action: => A): Future[A] = {
    val result = Promise[A]()
    val thread = new Thread(() => result.complete(Try(action)).discard(), "mutex-request")
    thread.setDaemon(true)
    thread.start()
    result.future
  }

  private def awaitContention(acquired: Future[Boolean], started: Promise[Unit]): Unit = {
    Await.result(started.future, watchdog)
    assertThrows[TimeoutException](Await.ready(acquired, 1.second)).discard()
  }

  private def withChild[A](own: String, other: String)(use: Child => A): A = Using.resource(new Child(own, other)) { child =>
    assert(child.awaitAny(Set("HELD")) == "HELD")
    use(child)
  }

  private final class Child(own: String, other: String) extends AutoCloseable {
    private val process = childJvm("izumi.fundamentals.platform.files.FileLockMutexProcess", own, other).redirectErrorStream(true).start()
    private val commands = new PrintWriter(process.getOutputStream, true)
    private val events = new LinkedBlockingQueue[String]()
    private val transcript = new ConcurrentLinkedQueue[String]()
    private val reader = new Thread(() => {
      val input = new BufferedReader(new InputStreamReader(process.getInputStream, StandardCharsets.UTF_8))
      try {
        var line = input.readLine()
        while (line != null) {
          transcript.add(line).discard()
          events.put(line)
          line = input.readLine()
        }
      } finally input.close()
    }, "mutex-process-output")
    reader.setDaemon(true)
    reader.start()

    def command(value: String): Unit = commands.println(value)

    def awaitAny(prefixes: Set[String]): String = {
      pollAny(prefixes, watchdog).getOrElse(fail(s"missing $prefixes; output=${transcript.asScala.mkString("\n")}"))
    }

    def pollAny(prefixes: Set[String], timeout: FiniteDuration): Option[String] = {
      val deadline = timeout.fromNow
      Iterator.continually(events.poll(math.max(0L, deadline.timeLeft.toNanos), TimeUnit.NANOSECONDS))
        .takeWhile(_ != null)
        .find(line => prefixes.exists(line.startsWith))
    }

    override def close(): Unit = {
      command("RELEASE")
      command("EXIT")
      if (!process.waitFor(watchdog.toMillis, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly().discard()
        assert(process.waitFor(watchdog.toMillis, TimeUnit.MILLISECONDS), "child process did not stop")
      }
      commands.close()
      reader.join(watchdog.toMillis)
      assert(!reader.isAlive, "child output reader did not stop")
      assert(process.exitValue() == 0, transcript.asScala.mkString("\n")).discard()
    }
  }
}
