package izumi.fixtures.host

import sbt.testing.{Event, EventHandler, Fingerprint, Framework, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, Task, TaskDef, TestSelector}

import java.util.concurrent.atomic.AtomicBoolean

abstract class ForeignSpec {
  def runBodies(): Unit
}

final class ForeignSuite extends ForeignSpec {
  override def runBodies(): Unit = {
    val audit = new BodyAudit(java.nio.file.Paths.get(sys.props("izumi.fixture.audit-root")), getClass.getName)
    (1 to 3).foreach(index => audit.record(index, None))
  }
}

final class ForeignFramework extends Framework {
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[ForeignSpec].getName
    override def requireNoArgConstructor(): Boolean = true
  }
  override def name(): String = "foreign-control-framework"
  override def fingerprints(): Array[Fingerprint] = Array(fingerprint)
  override def runner(arguments: Array[String], remoteArguments: Array[String], loader: ClassLoader): Runner = new Runner {
    override def args(): Array[String] = arguments
    override def remoteArgs(): Array[String] = remoteArguments
    override def done(): String = "foreign control complete"
    override def tasks(definitions: Array[TaskDef]): Array[Task] = definitions.map { definition => new Task {
      private val executed = new AtomicBoolean(false)
      override def taskDef(): TaskDef = definition
      override def tags(): Array[String] = Array.empty[String]
      override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
        val _ = loggers
        require(executed.compareAndSet(false, true), "Foreign task executed twice")
        val suite = Class.forName(definition.fullyQualifiedName(), true, loader).getConstructor().newInstance().asInstanceOf[ForeignSpec]
        suite.runBodies()
        Vector("first", "second", "third").foreach { path =>
          handler.handle(new Event {
            override def fullyQualifiedName(): String = definition.fullyQualifiedName()
            override def fingerprint(): Fingerprint = definition.fingerprint()
            override def selector(): Selector = new TestSelector("equal display name should " + path)
            override def status(): Status = Status.Success
            override def throwable(): OptionalThrowable = new OptionalThrowable()
            override def duration(): Long = 0L
          })
        }
        Array.empty[Task]
      }
    } }
  }
}
