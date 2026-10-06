package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*

import io.circe.{Json, parser}
import sbt.testing.{Event, EventHandler, Fingerprint, Framework as SbtFramework, Logger, NestedTestSelector, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, SuiteSelector, Task, TaskDef}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure as Failed, Success}

private[bootstrap] trait TargetControl {
  def close(): Future[Unit]
}

private[bootstrap] trait TargetRuntime {
  def context: ExecutionContext
  def newRunId(): RunId
  def await(completion: Future[Unit]): Unit
  def openControl(port: Int, application: TestApplication): Future[TargetControl]
}

private[bootstrap] class TargetFramework(runtime: TargetRuntime) extends SbtFramework {
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[TestSuite].getName
    override def requireNoArgConstructor(): Boolean = true
  }

  override def name(): String = "distage"
  override def fingerprints(): Array[Fingerprint] = Array(fingerprint)
  override def runner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader): Runner =
    new TargetRunner(args.clone(), remoteArgs.clone(), runtime)

  def slaveRunner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader, send: String => Unit): Runner =
    new TargetRunner(args.clone(), remoteArgs.clone(), runtime)
}

private[bootstrap] final class TargetRunner(arguments: Array[String], remoteArguments: Array[String], val runtime: TargetRuntime) extends Runner {
  private val ControlPortArgument = "--distage-control-port"
  private val MaximumControlPort = 65535
  private val TaskSchemaVersion = 1
  private val portIndices = arguments.indices.filter(index => arguments(index) == ControlPortArgument)
  require(portIndices.size <= 1, "Duplicate target control port")
  private val controlPort = portIndices.headOption.map { index =>
    require(index + 1 < arguments.length, "Missing target control port")
    val port = arguments(index + 1).toInt
    require(port > 0 && port <= MaximumControlPort, "Target control port is outside its valid range")
    port
  }
  private val requestArguments = portIndices.headOption.fold(arguments.toVector)(index => arguments.toVector.patch(index, Nil, 2))
  private val request = RequestArguments.parse(requestArguments).fold(error => throw new IllegalArgumentException(error.message), value => value)
  private val applications = scala.collection.mutable.Map.empty[RunId, TestApplication]
  private var spent = false

  override val args: Array[String] = arguments.clone()
  override def remoteArgs(): Array[String] = remoteArguments.clone()

  override def tasks(definitions: Array[TaskDef]): Array[Task] = synchronized {
    requireActive()
    validate(definitions.toVector)
    if (definitions.isEmpty) Array.empty
    else Array(new TargetTask(definitions.toVector.sortBy(_.fullyQualifiedName()), request, controlPort, this, reconstructed = false))
  }

  def serializeTask(task: Task, serialize: TaskDef => String): String = synchronized {
    requireActive()
    val selected = task match {
      case value: TargetTask if value.owner eq this => value
      case _ => throw new IllegalArgumentException("Task belongs to another target runner")
    }
    selected.consume()
    Json.obj("version" -> Json.fromInt(TaskSchemaVersion), "definitions" -> Json.arr(selected.definitions.map(definition => Json.fromString(serialize(definition)))* )).noSpaces
  }

  def deserializeTask(payload: String, deserialize: String => TaskDef): Task = synchronized {
    requireActive()
    require(payload.length <= ProtocolCodec.MaxFrameCharacters, "Serialized target task exceeds the frame limit")
    val cursor = parser.parse(payload).fold(throw _, value => value).hcursor
    require(cursor.get[Int]("version") == Right(TaskSchemaVersion), "Unsupported target task version")
    val definitions = cursor.get[Vector[String]]("definitions").fold(throw _, value => value).map(deserialize)
    require(definitions.nonEmpty, "Serialized target task has no suites")
    validate(definitions)
    new TargetTask(definitions, request, controlPort, this, reconstructed = true)
  }

  def receiveMessage(frame: String): Option[String] = {
    ProtocolCodec.decode(frame).fold(error => throw new IllegalArgumentException(error.message), value => value) match {
      case command: ProtocolMessage.Cancel =>
        val application = synchronized {
          requireActive()
          applications.getOrElse(command.run, throw new IllegalArgumentException("Cancellation refers to an inactive target run"))
        }
        application.accept(command).failed.foreach(cause => throw cause)(runtime.context)
        None
      case _ => throw new IllegalArgumentException("Target runner messages must be cancellation commands")
    }
  }

  override def done(): String = synchronized {
    requireActive()
    require(applications.isEmpty, "Target runner completed before its application cleanup")
    spent = true
    ""
  }

  def begin(application: TestApplication): Unit = synchronized {
    requireActive()
    require(!applications.contains(application.run), "Duplicate target application identity")
    applications.update(application.run, application)
  }

  def end(application: TestApplication): Unit = synchronized {
    require(applications.remove(application.run).contains(application), "Target application ownership was lost")
  }

  private def requireActive(): Unit = require(!spent, "Target runner is spent")

  private def validate(definitions: Vector[TaskDef]): Unit = {
    require(definitions.map(_.fullyQualifiedName()).distinct.size == definitions.size, "Duplicate target suite identities")
    definitions.foreach { definition =>
      require(definition.selectors().forall(_.isInstanceOf[SuiteSelector]), "Target tasks require suite selectors")
      definition.fingerprint() match {
        case value: SubclassFingerprint =>
          require(!value.isModule() && value.superclassName() == classOf[TestSuite].getName, "Target task fingerprint does not identify a test suite")
        case _ => throw new IllegalArgumentException("Target task fingerprint does not identify a test suite")
      }
    }
  }
}

private[bootstrap] final class TargetTask(
  val definitions: Vector[TaskDef],
  request: RunRequest,
  controlPort: Option[Int],
  val owner: TargetRunner,
  reconstructed: Boolean,
) extends Task {
  private var valid = true

  def consume(): Unit = synchronized {
    require(valid, "Target task was already consumed")
    valid = false
  }

  override def taskDef(): TaskDef = definitions.head
  override def tags(): Array[String] = Array.empty

  override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    owner.runtime.await(executeApplication(handler))
    Array.empty
  }

  def execute(handler: EventHandler, loggers: Array[Logger], complete: Array[Task] => Unit): Unit = {
    val _ = loggers
    implicit val ec: ExecutionContext = owner.runtime.context
    executeApplication(handler).onComplete {
      case Success(_) => complete(Array.empty)
      case Failed(cause) =>
        try handler.handle(new TargetTransportError(taskDef(), cause))
        finally complete(Array.empty)
    }
  }

  private def executeApplication(handler: EventHandler): Future[Unit] = {
    require(reconstructed, "Target task must be reconstructed from serialized identities")
    consume()
    implicit val ec: ExecutionContext = owner.runtime.context
    val output = new ProtocolOutput {
      override def accept(message: ProtocolMessage): Unit = handler.synchronized {
        handler.handle(new TargetChannelFrame(taskDef(), ProtocolCodec.encode(message)))
      }
    }
    val loader = new TargetSuiteLoader
    val factories = definitions.map(definition => () => loader.load(definition.fullyQualifiedName()))
    val application = new TestApplication(owner.runtime.newRunId(), request.identity, factories, ec, output)
    owner.begin(application)
    val control = controlPort match {
      case Some(port) => owner.runtime.openControl(port, application)
      case None => Future.successful(new TargetControl { override def close(): Future[Unit] = Future.unit })
    }
    control.flatMap { connection =>
      application.accept(ProtocolMessage.Request(RequestOperation.Execute, application.run, request)).transformWith { result =>
        connection.close().flatMap(_ => Future.fromTry(result))
      }
    }.andThen { case _ => owner.end(application) }
  }
}

private[bootstrap] final class TargetChannelFrame(definition: TaskDef, frame: String) extends Event {
  override def fullyQualifiedName(): String = definition.fullyQualifiedName()
  override def fingerprint(): Fingerprint = definition.fingerprint()
  override def selector(): Selector = new NestedTestSelector("$distage-protocol-v4", frame)
  override def status(): Status = Status.Success
  override def throwable(): OptionalThrowable = new OptionalThrowable
  override def duration(): Long = 0L
}

private[bootstrap] final class TargetTransportError(definition: TaskDef, cause: Throwable) extends Event {
  override def fullyQualifiedName(): String = definition.fullyQualifiedName()
  override def fingerprint(): Fingerprint = definition.fingerprint()
  override def selector(): Selector = new SuiteSelector
  override def status(): Status = Status.Error
  override def throwable(): OptionalThrowable = new OptionalThrowable(cause)
  override def duration(): Long = 0L
}
