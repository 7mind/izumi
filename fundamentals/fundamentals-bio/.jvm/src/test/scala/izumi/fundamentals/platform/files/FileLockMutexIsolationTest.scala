package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.net.URLClassLoader
import java.nio.channels.FileChannel
import java.nio.file.{Paths, StandardOpenOption}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import scala.util.Using

class FileLockMutexIsolationTest extends FileLockMutexFixture {
  "FileLockMutex" should {
    "keep its OS lock after a contender in another class loader exhausts its budget" in { mutexName =>
      val name = mutexName()
      def canAcquire(): Boolean = {
        val process = childJvm("izumi.fundamentals.platform.files.FileLockMutexProbe", name).inheritIO().start()
        try {
          assert(process.waitFor(10, TimeUnit.SECONDS))
          assert(Set(0, 73).contains(process.exitValue()))
          process.exitValue() == 0
        } finally process.destroyForcibly().discard()
      }

      Using.resource(new URLClassLoader(classpath.toArray, ClassLoader.getPlatformClassLoader)) {
        loader =>
          assert(loader.loadClass(FileLockMutex.getClass.getName) ne FileLockMutex.getClass)
          val contender = loader.loadClass(classOf[FileLockMutexProbe].getName).getConstructor().newInstance()
          FileLockMutex
            .allocate[Identity, Unit](name, 10.millis, 0, (_, _) => (), ())(
              _ => fail("unexpected overlap"),
              lock => {
                assert(!canAcquire())
                assert(contender.getClass.getMethod("tryMutex", classOf[String]).invoke(contender, name) == java.lang.Boolean.FALSE)
                assert(lock.isValid)
                assert(!canAcquire(), "external process acquired the still-held lock")
                ()
              },
            ).use(identity)
          assert(canAcquire())
      }
    }
  }
}

final class FileLockMutexProbe {
  def tryMutex(name: String): Boolean = FileLockMutex.withLocalMutex[Identity, Boolean](name, 10.millis, 0, (_, _) => (), ())(_ => false, _.isValid)
}

object FileLockMutexProbe {
  def main(args: Array[String]): Unit = {
    val path = Paths.get(System.getProperty("java.io.tmpdir"), s"${args(0)}.tmp")
    val acquired = Using.resource(FileChannel.open(path, StandardOpenOption.WRITE)) {
      channel =>
        Option(channel.tryLock()).exists {
          lock =>
            lock.release(); true
        }
    }
    if (!acquired) System.exit(73)
  }
}
