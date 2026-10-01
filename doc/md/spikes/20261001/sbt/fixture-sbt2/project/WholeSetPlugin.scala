import sbt._
import sbt.Keys._
import sbt.testing.{Framework, Runner, Fingerprint, TaskDef, Task => TestTask, EventHandler, Logger => TestLogger, Event, Selector, TestSelector, Status, OptionalThrowable}

object WholeSetPlugin extends AutoPlugin {
  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    Test / loadedTestFrameworks := Def.uncached {
      (Test / loadedTestFrameworks).value.map { case (key, framework) =>
        key -> (if (framework.name() == "discovery-only") new WholeSetFramework(framework) else framework)
      }
    }
  )
}

final class WholeSetFramework(delegate: Framework) extends Framework {
  def name(): String = "application-proxy"
  def fingerprints(): Array[Fingerprint] = delegate.fingerprints()
  def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner = new Runner {
    def args(): Array[String] = arguments
    def remoteArgs(): Array[String] = remoteArguments
    def done(): String = "whole-set proxy done"
    def tasks(defs: Array[TaskDef]): Array[TestTask] = {
      println("[spike] HOST_TASKS " + defs.map(_.fullyQualifiedName()).sorted.mkString(","))
      val application = loader.loadClass("spike.Application")
      val run = application.getMethod("run", classOf[Array[String]], classOf[Array[String]], classOf[ClassLoader], classOf[String])
      lazy val results = run.invoke(null, defs.map(_.fullyQualifiedName()).sorted, arguments, loader, "HOST").asInstanceOf[java.util.Map[String, java.util.List[String]]]
      defs.map { definition => new TestTask {
        def taskDef(): TaskDef = definition
        def tags(): Array[String] = Array.empty[String]
        def execute(handler: EventHandler, loggers: Array[TestLogger]): Array[TestTask] = {
          println("[spike] TASK_ENTER " + definition.fullyQualifiedName())
          val tests = results.get(definition.fullyQualifiedName()).iterator()
          while (tests.hasNext) {
            val testName = tests.next()
            handler.handle(new Event {
              def fullyQualifiedName(): String = definition.fullyQualifiedName()
              def fingerprint(): Fingerprint = definition.fingerprint()
              def selector(): Selector = new TestSelector(testName)
              def status(): Status = Status.Success
              def throwable(): OptionalThrowable = new OptionalThrowable()
              def duration(): Long = 1L
            })
          }
          println("[spike] TASK_EXIT " + definition.fullyQualifiedName())
          Array.empty[TestTask]
        }
      }}
    }
  }
}
