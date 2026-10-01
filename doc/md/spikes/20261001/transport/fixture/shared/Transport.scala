package transport

import sbt.testing._

abstract class SuiteBase

object TransportArguments {
  val FailB = "fail-b"
  val TwoGroups = "two-groups"
  val FirstOnly = "first-only"
  val AbortGroup = "abort-group"
  val ExitGroup = "exit-group"
  val ExitGroupStatus = 3
}

final class SuiteFingerprint extends SubclassFingerprint {
  def isModule(): Boolean = false
  def superclassName(): String = "transport.SuiteBase"
  def requireNoArgConstructor(): Boolean = true
}

final class TransportFramework extends Framework {
  def name(): String = "TransportSpike"
  def fingerprints(): Array[Fingerprint] = Array(new SuiteFingerprint)
  def runner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader): Runner =
    new TransportRunner(args, _ => ())
  def slaveRunner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader, send: String => Unit): Runner =
    new TransportRunner(args, send)
}

final class TransportRunner(val args: Array[String], send: String => Unit) extends Runner {
  private var pending = 0
  private var spent = false
  def remoteArgs(): Array[String] = Array.empty
  def tasks(defs: Array[TaskDef]): Array[Task] = {
    require(!spent, "spent runner")
    val suites = defs.map(_.fullyQualifiedName()).sorted
    println("TASKS " + suites.mkString(","))
    if (defs.isEmpty) Array.empty
    else {
      val groups = if (args.contains(TransportArguments.TwoGroups)) {
        defs.groupBy(d => if (d.fullyQualifiedName() < "transport.SuiteD") "ABC" else "DE").toSeq.sortBy(_._1).map(_._2)
      } else Seq(defs)
      groups.map { group =>
        val aggregate = group.minBy(_.fullyQualifiedName())
        new ApplicationTask(aggregate, group.map(_.fullyQualifiedName()).sorted, this): Task
      }.toArray
    }
  }
  def started(): Unit = { pending += 1 }
  def finished(): Unit = { pending -= 1; send("complete") }
  def receiveMessage(msg: String): Option[String] = { println("MESSAGE " + msg); None }
  def serializeTask(task: Task, serializer: TaskDef => String): String = {
    val app = task.asInstanceOf[ApplicationTask]
    app.invalidate()
    println("SERIALIZE " + app.suites.mkString(","))
    app.suites.mkString(",") + "\n" + serializer(app.taskDef())
  }
  def deserializeTask(payload: String, deserializer: String => TaskDef): Task = {
    val split = payload.indexOf('\n')
    require(split >= 0, "missing serialized task boundary")
    val suites = payload.substring(0, split).split(",")
    println("DESERIALIZE " + suites.mkString(","))
    new ApplicationTask(deserializer(payload.substring(split + 1)), suites, this)
  }
  def done(): String = { require(!spent && pending == 0, "completion before callback"); spent = true; "" }
}

final class ApplicationTask(definition: TaskDef, val suites: Array[String], owner: TransportRunner) extends Task {
  private var valid = true
  def invalidate(): Unit = { require(valid); valid = false }
  def taskDef(): TaskDef = definition
  def tags(): Array[String] = Array.empty
  def execute(events: EventHandler, loggers: Array[Logger]): Array[Task] = {
    require(valid, "serialized task reused")
    failIfRequested()
    owner.started()
    run(events, loggers)
    owner.finished()
    Array.empty
  }
  def execute(events: EventHandler, loggers: Array[Logger], complete: Array[Task] => Unit): Unit = {
    require(valid, "serialized task reused")
    failIfRequested()
    owner.started()
    Platform.defer(() => {
      run(events, loggers)
      owner.finished()
      complete(Array.empty)
    })
  }
  private def failIfRequested(): Unit = {
    if (owner.args.contains(TransportArguments.AbortGroup)) {
      println("ABORT " + suites.mkString(","))
      throw new IllegalStateException("deliberate aggregate failure before any event")
    }
    if (owner.args.contains(TransportArguments.ExitGroup)) {
      println("EXIT " + suites.mkString(","))
      Platform.exit(TransportArguments.ExitGroupStatus)
    }
  }
  private def run(events: EventHandler, loggers: Array[Logger]): Unit = {
    val testsPerSuite = if (owner.args.contains(TransportArguments.FirstOnly)) 1 else StubApplication.TestsPerSuite
    val application = new StubApplication(suites, testsPerSuite)
    application.execute((suite, test) => {
      println("BODY " + suite + "#" + test)
      val failed = owner.args.contains(TransportArguments.FailB) && suite == "transport.SuiteB" && test == "test1"
      events.handle(new Event {
        def fullyQualifiedName(): String = definition.fullyQualifiedName()
        def fingerprint(): Fingerprint = definition.fingerprint()
        def selector(): Selector = new NestedTestSelector(suite, test)
        def status(): Status = if (failed) Status.Failure else Status.Success
        def throwable(): OptionalThrowable = if (failed) new OptionalThrowable(new AssertionError("deliberate SuiteB failure")) else new OptionalThrowable()
        def duration(): Long = 1L
      })
    })
    val report = application.report
    require(report.acquired == 1 && report.released == 1, "resource lifetime")
    require(report.bodies == suites.length * testsPerSuite, "selected body count")
    loggers.foreach(_.info("CHECK suites=" + suites.mkString(",") + " bodies=" + report.bodies +
      " acquire=" + report.acquired + " release=" + report.released + " platform=" + Platform.name + " pid=" + Platform.processId))
  }
}

final case class ResourceReport(acquired: Int, released: Int, bodies: Int)
final class StubApplication(suites: Array[String], testsPerSuite: Int) {
  private var acquired = 0
  private var released = 0
  private var bodies = 0
  def execute(body: (String, String) => Unit): Unit = {
    acquired += 1
    try suites.foreach { suite =>
      (1 to testsPerSuite).foreach { index =>
        bodies += 1
        body(suite, "test" + index)
      }
    } finally released += 1
  }
  def report: ResourceReport = ResourceReport(acquired, released, bodies)
}
object StubApplication { val TestsPerSuite = 3 }
