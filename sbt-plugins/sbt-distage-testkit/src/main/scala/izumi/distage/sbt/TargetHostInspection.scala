package izumi.distage.sbt

import izumi.distage.testkit.protocol.*
import sbt.testing.{Event, EventHandler, Framework, NestedTestSelector, SuiteSelector, TaskDef}
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal

private[sbt] object TargetHostInspection {
  def run(platform: Framework, definitions: Vector[TaskDef], request: RunRequest, operation: RequestOperation, log: String => Unit): Unit = {
    require(definitions.nonEmpty, "Distage inspection found no suite definitions")
    val name = operation match {
      case RequestOperation.Resolve => "list"
      case RequestOperation.Plan => "plan"
      case RequestOperation.Execute => throw new IllegalArgumentException("Inspection must resolve or plan")
    }
    val control = new TargetHostControl
    val outcome = new AtomicReference[Either[Throwable, ProtocolMessage]](Left(new IllegalStateException("Target inspection ended without an outcome")))
    val worker = new Thread(() => {
      var result: Either[Throwable, ProtocolMessage] = Left(new IllegalStateException("Target inspection ended without an outcome"))
      try {
        val args = RequestArguments.render(request) ++ Vector("--distage-operation", name, "--distage-control-port", control.port.toString)
        val runner = platform.runner(args.toArray, Array.empty, getClass.getClassLoader)
        try {
          val buffer = new InspectionEvents(request, operation, control)
          val tasks = runner.tasks(definitions.toArray)
          require(tasks.length == 1, "Target inspection requires one SDK aggregate")
          require(tasks.head.execute(buffer, Array.empty).isEmpty, "Target inspection returned nested tasks")
          result = buffer.result()
        } finally { val _ = runner.done() }
      } catch {
        case cause: InterruptedException => result = Left(cause)
        case NonFatal(cause) => result = Left(cause)
      } finally {
        try control.close()
        catch { case NonFatal(cause) => result match {
          case Left(original) => original.addSuppressed(cause)
          case Right(_) => result = Left(cause)
        } }
        outcome.set(result)
      }
    }, "distage-target-inspection-" + control.port)
    worker.start()
    var interruption = Option.empty[InterruptedException]
    while (worker.isAlive) {
      try worker.join()
      catch { case cause: InterruptedException => interruption = Some(cause); control.cancel() }
    }
    interruption.foreach(throw _)
    outcome.get().fold(throw _, response => log("DISTAGE_INSPECTION " + ProtocolCodec.encode(response)))
  }

  private final class InspectionEvents(request: RunRequest, operation: RequestOperation, control: TargetHostControl) extends EventHandler {
    private var run = Option.empty[RunId]
    private var response = Option.empty[ProtocolMessage]
    private var failure = Option.empty[Throwable]

    override def handle(event: Event): Unit = synchronized {
      if (failure.isEmpty) {
        try event.selector() match {
          case nested: NestedTestSelector if nested.suiteId() == "$distage-protocol-v4" =>
            val message = ProtocolCodec.decode(nested.testName()).fold(error => throw new IllegalArgumentException(error.message), identity)
            require(response.isEmpty, "Target inspection emitted more than one terminal response")
            message match {
              case ProtocolMessage.Discovered(id, catalogue) =>
                require(run.isEmpty && catalogue.identity == request.identity, "Target inspection discovery has another identity")
                run = Some(id)
                control.started(id)
              case resolved: ProtocolMessage.Resolved =>
                require(operation == RequestOperation.Resolve && run.contains(resolved.run), "Unexpected target resolution")
                response = Some(resolved)
              case planned: ProtocolMessage.Planned =>
                require(operation == RequestOperation.Plan && run.contains(planned.run), "Unexpected target plan")
                require(planned.plan.inspection.failures.isEmpty, "Distage inspection contains planning failures")
                response = Some(planned)
              case ProtocolMessage.Rejected(_, cause) => throw ProjectedFailure.root(cause)
              case _ => throw new IllegalArgumentException("Unexpected target inspection response")
            }
          case _: SuiteSelector if event.throwable().isDefined => throw event.throwable().get()
          case _ => throw new IllegalArgumentException("Unattributable target inspection SDK event")
        } catch { case NonFatal(cause) => failure = Some(cause); control.cancel() }
      }
    }

    def result(): Either[Throwable, ProtocolMessage] = synchronized {
      failure match {
        case Some(cause) => Left(cause)
        case None => response.toRight(new IllegalStateException("Target inspection returned without its requested response"))
      }
    }
  }
}
