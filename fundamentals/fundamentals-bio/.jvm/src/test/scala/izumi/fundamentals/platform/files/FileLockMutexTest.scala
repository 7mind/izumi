package izumi.fundamentals.platform.files

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner, QuasiTemporal}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import org.scalatest.Outcome
import org.scalatest.wordspec.FixtureAnyWordSpec

import java.io.{BufferedReader, File, IOException, InputStreamReader, PrintWriter}
import java.net.URLClassLoader
import java.nio.channels.{FileLock, OverlappingFileLockException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.UUID
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, LinkedBlockingQueue, TimeUnit}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
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
        val sleeping = new CountDownLatch(1)
        val acquired = start {
          mutex(name, 0, observingTimer(sleeping), (_, _) => fail("unexpected overlap retry"), new OnPendingFileLock(() => sleeping.countDown()))(
            _ => fail("external ownership exhausted the same-process budget"),
            _.isValid,
          ).use(identity)
        }
        awaitContention(acquired, sleeping, name)
        assert(!acquired.isCompleted)
        child.command("RELEASE")
        assert(Await.result(acquired, watchdog))
      }
    }

    "allow independent requests in two processes without a false deadlock" in { mutexName =>
      val first = mutexName()
      val second = mutexName()
      withChild(second, first) { child =>
        val (_, release) = mutex(first, 0, timer, (_, _) => ())(_ => (), _ => ()).unsafeAllocate()
        val sleeping = new CountDownLatch(1)
        val acquired = start {
          mutex(second, 0, observingTimer(sleeping), (_, _) => fail("unexpected overlap retry"), new OnPendingFileLock(() => if (!hasProcLocks) sleeping.countDown()))(
            _ => fail("unexpected exhaustion"),
            _.isValid,
          ).use(identity)
        }
        val outcome = try {
          awaitContention(acquired, sleeping, second)
          child.command("WAIT")
          child.awaitAny(if (hasProcLocks) Set("SLEEP", "ERROR") else Set("SUBMITTED", "SLEEP", "ERROR"))
        } finally release()
        child.command("RELEASE")
        assert(Await.result(acquired, watchdog))
        assert(!outcome.startsWith("ERROR"), outcome)
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

    "release a pending file lock when a MiniBIOAsync waiter is interrupted" in { mutexName =>
      assume(Files.isRegularFile(Paths.get("/proc/locks")))
      implicit val ec: ExecutionContext = ExecutionContext.global
      type Task[A] = MiniBIOAsync[Throwable, A]
      val name = mutexName()
      withChild(name, mutexName()) { child =>
        val (result, interrupt) = QuasiIORunner[Task].runFutureInterruptible {
          FileLockMutex.withLocalMutex[Task, Boolean](name, retryWait, 0, (_, _) => QuasiIO[Task].unit, QuasiIO[Task].unit)(
            _ => QuasiIO[Task].fail(new AssertionError("unexpected exhaustion")),
            _ => QuasiIO[Task].pure(true),
          )
        }
        try {
          awaitContention(result, new CountDownLatch(1), name)
          Await.result(interrupt(), watchdog)
          assert(Await.ready(result, watchdog).value.exists(_.isFailure))
        } finally child.command("RELEASE")
        assert(mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).use(identity))
      }
    }

    "interrupt a ZIO wait through BIO before its owner releases the lock" in { mutexName =>
      assume(hasProcLocks)
      val name = mutexName()
      withChild(name, mutexName()) { child =>
        val succeeded = new CountDownLatch(1)
        val wait = FileLockMutex.withLocalMutex[zio.Task, Boolean](name, retryWait, 0, (_, _) => zio.ZIO.unit, zio.ZIO.unit)(
          _ => zio.ZIO.fail(new AssertionError("unexpected exhaustion")),
          _ => zio.ZIO.succeed { succeeded.countDown(); true },
        )(QuasiIO.fromBIO[zio.IO], QuasiAsync.fromBIO[zio.IO], QuasiTemporal.fromBIO[zio.IO])
        val result = zio.Unsafe.unsafe { implicit unsafe => zio.Runtime.default.unsafe.runToFuture(wait) }
        try {
          awaitContention(result, new CountDownLatch(1), name)
          assert(Await.result(result.cancel(), watchdog).isInterrupted)
          assert(succeeded.getCount == 1L)
        } finally child.command("RELEASE")
        assert(mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).use(identity))
      }
    }

    "cancel an external wait before its owner releases the lock" in { mutexName =>
      assume(Files.isRegularFile(Paths.get("/proc/locks")))
      val name = mutexName()
      withChild(name, mutexName()) { child =>
        val succeeded = new CountDownLatch(1)
        val (result, cancelWait) = FileLockMutex.withLocalMutex[IO, Boolean](name, retryWait, 0, (_, _) => IO.unit, IO.unit)(
          _ => IO.raiseError(new AssertionError("unexpected exhaustion")),
          _ => IO { succeeded.countDown(); true },
        ).unsafeToFutureCancelable()(IORuntime.global)
        try {
          awaitContention(result, new CountDownLatch(1), name)
          Await.result(cancelWait(), watchdog)
          assert(succeeded.getCount == 1L)
        } finally child.command("RELEASE")
        assert(mutex(name, 0, timer, (_, _) => ())(_ => false, _.isValid).use(identity))
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

  private def hasProcLocks: Boolean = Files.isRegularFile(Paths.get("/proc/locks"))

  private def mutex[A](
    name: String,
    maximum: Int,
    temporal: QuasiTemporal[Identity],
    attemptLog: (Int, Int) => Unit,
    async: QuasiAsync[Identity] = QuasiAsync.quasiAsyncIdentity,
  )(failure: Int => A,
    success: FileLock => A,
  ): Lifecycle[Identity, A] = FileLockMutex.allocate[Identity, A](name, retryWait, maximum, attemptLog, ())(
    failure,
    success,
  )(QuasiIO.quasiIOIdentity, async, temporal)

  private def observingTimer(sleeping: CountDownLatch): QuasiTemporal[Identity] = new QuasiTemporal[Identity] {
    override def sleep(duration: FiniteDuration): Unit = {
      sleeping.countDown()
      timer.sleep(duration)
    }
  }

  private def start[A](action: => A): Future[A] = {
    val result = Promise[A]()
    val thread = new Thread(() => result.complete(Try(action)).discard(), "mutex-request")
    thread.setDaemon(true)
    thread.start()
    result.future
  }

  private def awaitContention(acquired: Future[Boolean], sleeping: CountDownLatch, name: String): Unit = {
    val deadline = watchdog.fromNow
    val locks = Paths.get("/proc/locks")
    def blocked: Boolean = if (Files.isRegularFile(locks)) {
      val file = Paths.get(System.getProperty("java.io.tmpdir"), s"$name.tmp")
      val inode = Files.getAttribute(file, "unix:ino").toString
      val blockedOwner = s"->\\s+POSIX\\s+ADVISORY\\s+WRITE\\s+${ProcessHandle.current().pid()}\\s+\\S+:$inode\\s".r
      blockedOwner.findFirstIn(new String(Files.readAllBytes(locks), StandardCharsets.UTF_8)).isDefined
    } else false
    while (!sleeping.await(retryWait.toMillis, TimeUnit.MILLISECONDS) && !blocked) {
      assert(!acquired.isCompleted, s"mutex completed before release: ${acquired.value}")
      assert(deadline.hasTimeLeft(), "no observed external-process contention")
    }
    assert(!acquired.isCompleted, s"mutex completed before release: ${acquired.value}").discard()
  }

  private def withChild[A](own: String, other: String)(use: Child => A): A = Using.resource(new Child(own, other)) { child =>
    assert(child.awaitAny(Set("HELD")) == "HELD")
    use(child)
  }

  private def childClasspath(): String = {
    val loaders = Iterator.iterate[ClassLoader](getClass.getClassLoader)(_.getParent).takeWhile(_ != null)
    val urls = loaders.collect { case loader: URLClassLoader => loader.getURLs.toList }.flatten
    val locations = urls.map(url => Paths.get(url.toURI).toString).toList
    (locations ++ System.getProperty("java.class.path").split(File.pathSeparator).toList).distinct.mkString(File.pathSeparator)
  }

  private final class Child(own: String, other: String) extends AutoCloseable {
    private val process = new ProcessBuilder(
      Paths.get(System.getProperty("java.home"), "bin", "java").toString,
      "-Xmx128m",
      s"-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}",
      "-cp",
      childClasspath(),
      "izumi.fundamentals.platform.files.FileLockMutexProcess",
      own,
      other,
    ).redirectErrorStream(true).start()
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
      val deadline = watchdog.fromNow
      var matched = Option.empty[String]
      while (matched.isEmpty) {
        val line = events.poll(math.max(0L, deadline.timeLeft.toNanos), TimeUnit.NANOSECONDS)
        assert(line != null, s"missing $prefixes; output=${transcript.asScala.mkString("\n")}")
        if (prefixes.exists(prefix => line.startsWith(prefix))) matched = Some(line)
      }
      matched.get
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
