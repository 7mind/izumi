package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

final class TestApplication(
  val run: RunId,
  identity: CatalogueIdentity,
  factories: Vector[() => TestSuite],
  executionContext: ExecutionContext,
  output: ProtocolOutput,
) {
  require(run.value.nonEmpty, "Application run identity must not be empty")

  private implicit val ec: ExecutionContext = executionContext
  private val session: RunSession = new RunSession(identity, factories, executionContext, new EventSink {
    override def accept(event: ProtocolMessage.Event): Unit = {
      event.event match {
        case _: RunEvent.Finished => finishExecution()
        case _ => ()
      }
      // Return through the provider's cleanup path before failing the command on a lost channel.
      if (deliver(event).isLeft) session.cancel()
    }
  })
  private var queued = Future.unit
  private var resolution = Option.empty[(RunRequest, Either[Failure, ResolvedRun])]
  private var planning = Option.empty[(RunRequest, Either[Failure, PlannedRun])]
  private var executionStarted = false
  private var executionCompleted = false
  private val deliveryMonitor = new Object
  private var deliveryFailure = Option.empty[Throwable]

  def accept(message: ProtocolMessage): Future[Unit] = message match {
    case command: ProtocolMessage.Discover => acceptCommand(command.run, command) {
      if (command.build != identity.build || command.target != identity.target) reject(command.run, FailurePhase.Discovery, "Discovery refers to another build target")
      else respond(session.discover().map(ProtocolMessage.Discovered(run, _)), command.run)
    }
    case command: ProtocolMessage.Request => acceptCommand(command.run, command) {
      if (executionStarted) reject(command.run, FailurePhase.Selection, "Application execution has already started")
      else command.operation match {
        case RequestOperation.Resolve => respond(resolve(command.request).map(value => ProtocolMessage.Resolved(run, value.description)), run)
        case RequestOperation.Plan => plan(command.request).flatMap(result => respond(result.map(value => ProtocolMessage.Planned(run, value.description)), run))
        case RequestOperation.Execute => plan(command.request).flatMap {
          case Left(failure) => respond(Left(failure), run)
          case Right(prepared) =>
            executionStarted = true
            session.execute(run, prepared).map { outcome =>
              write(ProtocolMessage.Completed(outcome))
            }.andThen { case _ => finishExecution() }
        }
      }
    }
    case command: ProtocolMessage.Cancel =>
      if (command.run == run && ProtocolCodec.validate(command).isRight) {
        val completed = synchronized(executionCompleted)
        if (completed) enqueue(reject(run, FailurePhase.Transport, "Application execution has already completed"))
        else {
          session.cancel()
          Future.successful(())
        }
      } else acceptCommand(command.run, command)(Future.unit)
    case _ => Future.failed(new IllegalArgumentException("Application input must be a discovery, request or cancellation command"))
  }

  private def acceptCommand(commandRun: RunId, command: ProtocolMessage)(operation: => Future[Unit]): Future[Unit] = {
    if (commandRun.value.isEmpty) Future.failed(new IllegalArgumentException("Command run identity must not be empty"))
    else enqueue {
      if (commandRun != run) reject(commandRun, FailurePhase.Transport, "Command refers to another application run")
      else ProtocolCodec.validate(command) match {
        case Left(error) => reject(commandRun, FailurePhase.Selection, error.message)
        case Right(_) => operation
      }
    }
  }

  private def enqueue(operation: => Future[Unit]): Future[Unit] = {
    val (previous, next) = synchronized {
      val previous = queued
      val next = Promise[Unit]()
      // Publish the tail before an inline execution context can reenter through output delivery.
      queued = next.future
      (previous, next)
    }
    next.completeWith(previous.flatMap(_ => operation))
    next.future
  }

  private def finishExecution(): Unit = synchronized { executionCompleted = true }

  private def resolve(request: RunRequest): Either[Failure, ResolvedRun] = planning match {
    case Some((previous, _)) if previous != request => Left(RunnerFailure.message(FailurePhase.Selection, "Cannot change the request after planning has started"))
    case _ => resolution match {
      case Some((previous, result)) if previous == request => result
      case _ =>
        val result = session.resolve(request)
        resolution = Some(request -> result)
        result
    }
  }

  private def plan(request: RunRequest): Future[Either[Failure, PlannedRun]] = planning match {
    case Some((previous, result)) =>
      if (previous == request) Future.successful(result)
      else Future.successful(Left(RunnerFailure.message(FailurePhase.Selection, "Cannot change the request after planning has started")))
    case None => resolve(request) match {
      case Left(failure) => Future.successful(Left(failure))
      case Right(selected) => session.plan(selected).map { result =>
        planning = Some(request -> result)
        result
      }
    }
  }

  private def respond(result: Either[Failure, ProtocolMessage], commandRun: RunId): Future[Unit] = {
    write(result.fold(ProtocolMessage.Rejected(commandRun, _), value => value))
    Future.unit
  }

  private def reject(commandRun: RunId, phase: FailurePhase, reason: String): Future[Unit] = respond(Left(RunnerFailure.message(phase, reason)), commandRun)

  private def write(message: ProtocolMessage): Unit = deliver(message).fold(throw _, _ => ())

  private def deliver(message: ProtocolMessage): Either[Throwable, Unit] = deliveryMonitor.synchronized {
    deliveryFailure match {
      case Some(cause) => Left(cause)
      case None =>
        try {
          ProtocolCodec.validate(message).fold(error => throw new IllegalArgumentException(error.message), _ => ())
          output.accept(message)
          Right(())
        } catch {
          case NonFatal(cause) => deliveryFailure = Some(cause); Left(cause)
        }
    }
  }
}
