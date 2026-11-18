package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.io.File
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
      val urls = Iterator
        .iterate(getClass.getClassLoader)(_.getParent).takeWhile(_ != null).collect {
          case loader: URLClassLoader => loader.getURLs.toList
        }.flatten.toList ++ System.getProperty("java.class.path").split(File.pathSeparator).map(p => Paths.get(p).toUri.toURL).toList
      val classpath = urls.distinct.map(u => Paths.get(u.toURI).toString).mkString(File.pathSeparator)
      def canAcquire(): Boolean = {
        val process = new ProcessBuilder(
          Paths.get(System.getProperty("java.home"), "bin", "java").toString,
          "-Xmx128m",
          s"-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}",
          "-cp",
          classpath,
          "izumi.fundamentals.platform.files.FileLockMutexProbe",
          name,
        ).inheritIO().start()
        try {
          assert(process.waitFor(10, TimeUnit.SECONDS))
          assert(Set(0, 73).contains(process.exitValue()))
          process.exitValue() == 0
        } finally process.destroyForcibly().discard()
      }

      Using.resource(new URLClassLoader(urls.distinct.toArray, ClassLoader.getPlatformClassLoader)) {
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
