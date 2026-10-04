package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure as Failed, Success, Try}
import scala.util.control.NonFatal

final case class ApplicationResult(successful: Boolean, outcome: Option[RunOutcome])

object ApplicationLauncher {
  def run(
    identity: CatalogueIdentity,
    factories: Vector[() => TestSuite],
    context: ExecutionContext,
    source: ProtocolFrameSource,
    output: ProtocolOutput,
  ): Future[ApplicationResult] = {
    implicit val ec: ExecutionContext = context
    val observed = new ObservedOutput(output)
    var application = Option.empty[TestApplication]
    var commands = Vector.empty[Future[Unit]]
    var inputFailure = Option.empty[Throwable]
    try {
      var frame = source.readFrame()
      while (frame.nonEmpty) {
        val message = ProtocolCodec.decode(frame.get).fold(error => throw new IllegalArgumentException(error.message), value => value)
        val run = message match {
          case command: ProtocolMessage.Discover => command.run
          case command: ProtocolMessage.Request => command.run
          case command: ProtocolMessage.Cancel => command.run
          case _ => throw new IllegalArgumentException("Launcher input must contain application commands")
        }
        val active = application match {
          case Some(value) => value
          case None =>
            val value = new TestApplication(run, identity, factories, context, observed)
            application = Some(value)
            value
        }
        commands :+= active.accept(message)
        frame = source.readFrame()
      }
      require(commands.nonEmpty, "Launcher input channel must contain a command")
    } catch {
      case NonFatal(cause) =>
        inputFailure = Some(cause)
        application.foreach { value => commands :+= value.accept(ProtocolMessage.Cancel(value.run)) }
    }
    val settled: Future[Vector[Try[Unit]]] = Future.sequence(commands.map(_.transform(result => Success(result))))
    settled.flatMap { results =>
      inputFailure.orElse(results.collectFirst { case Failed(cause) => cause }) match {
        case Some(cause) => Future.failed(cause)
        case None => Future.successful(observed.result)
      }
    }
  }

  private final class ObservedOutput(delegate: ProtocolOutput) extends ProtocolOutput {
    private var successful = true
    private var responses = 0
    private var outcome = Option.empty[RunOutcome]

    override def accept(message: ProtocolMessage): Unit = synchronized {
      delegate.accept(message)
      message match {
        case _: ProtocolMessage.Discovered | _: ProtocolMessage.Resolved => responses += 1
        case ProtocolMessage.Planned(_, plan) =>
          responses += 1
          successful = successful && plan.inspection.failures.isEmpty
        case ProtocolMessage.Completed(value) =>
          responses += 1
          outcome = Some(value)
          successful = successful && value.successful
        case _: ProtocolMessage.Rejected => responses += 1; successful = false
        case _: ProtocolMessage.Event => ()
        case _ => throw new IllegalStateException("Application emitted a command as output")
      }
    }

    def result: ApplicationResult = synchronized(ApplicationResult(successful && responses > 0, outcome))
  }
}
