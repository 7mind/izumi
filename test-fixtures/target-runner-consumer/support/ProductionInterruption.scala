import sbt.*
import sbt.Keys.*
import sbt.testing.{EventHandler, Fingerprint, Framework, Logger, Runner, Task, TaskDef}
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

object ProductionJsInterruptionPlugin extends AutoPlugin {
  override def requires: Plugins = izumi.distage.sbt.DistageTestkitJsPlugin
  override def projectSettings: Seq[Def.Setting[?]] = ProductionInterruption.settings
}

object ProductionNativeInterruptionPlugin extends AutoPlugin {
  override def requires: Plugins = izumi.distage.sbt.DistageTestkitNativePlugin
  override def projectSettings: Seq[Def.Setting[?]] = ProductionInterruption.settings
}

object ProductionInterruption {
  val settings: Seq[Def.Setting[?]] = Seq(
    Test / loadedTestFrameworks := Def.uncached {
      (Test / loadedTestFrameworks).value.map { case (key, framework) =>
        key -> (if (key == TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")) new InterruptionFramework(framework) else framework)
      }
    },
  )

  private class InterruptionFramework(delegate: Framework) extends Framework {
    override def name(): String = delegate.name()
    override def fingerprints(): Array[Fingerprint] = delegate.fingerprints()
    override def runner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader): Runner = {
      val original = delegate.runner(args, remoteArgs, loader)
      val armed = sys.props("candidate.interrupt") == "true"
      val claimed = new AtomicBoolean(false)
      new Runner {
        override def args(): Array[String] = original.args()
        override def remoteArgs(): Array[String] = original.remoteArgs()
        override def done(): String = original.done()
        override def tasks(definitions: Array[TaskDef]): Array[Task] = original.tasks(definitions).map(task => new Task {
          override def taskDef(): TaskDef = task.taskDef()
          override def tags(): Array[String] = task.tags()
          override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
            if (!armed || !claimed.compareAndSet(false, true)) task.execute(handler, loggers)
            else {
              val directory = Paths.get(sys.props("candidate.frames"))
              def files(): Set[java.nio.file.Path] = {
                val stream = Files.list(directory)
                try stream.iterator().asScala.toSet finally stream.close()
              }
              val prior = files()
              val caller = Thread.currentThread()
              val interrupted = new AtomicBoolean(false)
              val finished = new AtomicBoolean(false)
              val probe = new Thread(() => {
                while (!finished.get() && !interrupted.get()) {
                  if ((files() -- prior).exists(path => Files.readString(path).contains("\"testStarted\""))) {
                    println("SDK_EXECUTION_INTERRUPT target=" + caller.getName)
                    interrupted.set(true)
                    caller.interrupt()
                  } else Thread.sleep(20L)
                }
              }, "production-host-interruption-probe")
              probe.start()
              try task.execute(handler, loggers)
              finally {
                finished.set(true)
                probe.join()
                require(interrupted.get(), "Target returned before the cancellation probe observed a running test")
              }
            }
          }
        })
      }
    }
  }
}
