package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.util.Failure

object InspectionLauncher {
  def main(arguments: Array[String]): Unit = {
    require(arguments.nonEmpty, "Inspection requires list or plan, request arguments, -- and suite classes")
    val operation = arguments.head match {
      case "list" => RequestOperation.Resolve
      case "plan" => RequestOperation.Plan
      case other => throw new IllegalArgumentException("Unknown inspection operation: " + other)
    }
    val separator = arguments.indexOf("--")
    require(separator > 0 && separator < arguments.length - 1, "Inspection requires request arguments before -- and suite classes after it")
    val request = RequestArguments.parse(arguments.slice(1, separator).toVector).fold(error => throw new IllegalArgumentException(error.message), value => value)
    val suites = arguments.drop(separator + 1).toVector
    require(suites.forall(_.nonEmpty) && suites.distinct == suites, "Inspection suite classes must be nonempty and distinct")
    val loader = getClass.getClassLoader
    val factories = suites.map(name => () => JvmSuiteLoader.load(name, loader))
    var response = Option.empty[ProtocolMessage]
    val output = new ProtocolOutput {
      override def accept(message: ProtocolMessage): Unit = {
        require(response.isEmpty, "Inspection emitted more than one response")
        response = Some(message)
        println("DISTAGE_INSPECTION " + ProtocolCodec.encode(message))
      }
    }
    val executor = Executors.newWorkStealingPool()
    val context = ExecutionContext.fromExecutorService(executor)
    val application = new TestApplication(RunId(UUID.randomUUID().toString), request.identity, factories, context, output)
    val completion = try {
      val response = application.accept(ProtocolMessage.Request(operation, application.run, request))
      RunnerCompletion.after(response, () => application.close())(context)
        .transform(result => StandaloneLauncher.releaseContext(context, result))(ExecutionContext.global)
    } catch { case cause: Throwable => throw StandaloneLauncher.releaseContext(context, Failure(cause)).failed.get }
    StandaloneLauncher.awaitCompletion(completion, () => application.cancel())
    response match {
      case Some(_: ProtocolMessage.Resolved) if operation == RequestOperation.Resolve => ()
      case Some(ProtocolMessage.Planned(_, plan)) if operation == RequestOperation.Plan && plan.inspection.failures.isEmpty => ()
      case Some(ProtocolMessage.Rejected(_, failure)) => throw new IllegalStateException(s"Distage inspection rejected: ${failure.phase}: ${failure.exceptionClass}: ${failure.message}")
      case Some(_: ProtocolMessage.Planned) => throw new IllegalStateException("Distage inspection contains planning failures")
      case _ => throw new IllegalStateException("Inspection returned without the requested response")
    }
  }
}
