package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

final case class ProviderId(value: String) extends AnyVal

trait TestSuite {
  def register(context: RegistrationContext): RegisteredSuite
}

final case class RegisteredSuite(descriptor: SuiteDescriptor, tests: Vector[TestDescriptor], provider: ExecutionProvider)

trait ExecutionProvider {
  def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]]
  def plan(tests: Vector[TestDescriptor]): Future[ExecutionPlan]
}

trait ExecutionPlan {
  val tests: Vector[TestDescriptor]
  def execute(context: RunExecutionContext): Future[ProviderOutcome]
}

final case class ProviderOutcome(results: Vector[TestResult], failures: Vector[Failure], cancelled: Boolean)

trait EventSink {
  def accept(event: ProtocolMessage.Event): Unit
}

final class Cancellation {
  private val requested = new AtomicBoolean(false)
  def request(): Unit = requested.set(true)
  def isRequested: Boolean = requested.get()
}

sealed trait ProviderEvent
object ProviderEvent {
  final case class TestStarted(test: TestId) extends ProviderEvent
  final case class TestCompleted(result: TestResult) extends ProviderEvent
  final case class PhaseFailed(failure: Failure) extends ProviderEvent
}

final case class RunExecutionContext(run: RunId, cancellation: Cancellation, emit: ProviderEvent => Unit)

final class RegistrationContext(val target: BuildTargetId, val executionContext: ExecutionContext) {
  private val providers = mutable.Map.empty[ProviderId, ExecutionProvider]
  private var frozen = false

  def provider[P <: ExecutionProvider: ClassTag](id: ProviderId, create: () => P): P = {
    require(!frozen, "Registration is already frozen")
    require(id.value.nonEmpty, "Provider identity must not be empty")
    providers.get(id) match {
      case Some(existing) =>
        implicitly[ClassTag[P]].unapply(existing).getOrElse(throw new IllegalStateException(s"Provider identity has conflicting types: ${id.value}"))
      case None =>
        val created = create()
        providers.update(id, created)
        created
    }
  }

  private[runner] def freeze(): Unit = { frozen = true }
}

final class TestCancelled(message: String) extends RuntimeException(message)

object RunnerFailure {
  def message(phase: FailurePhase, message: String): Failure = Failure(phase, "TestApplicationError", message, Vector.empty, Vector.empty, None)

  private[runner] def unreported(reported: Vector[Failure], returned: Vector[Failure]): Vector[Failure] = {
    var remaining = reported
    returned.filter { failure =>
      val index = remaining.indexOf(failure)
      if (index < 0) true
      else { remaining = remaining.patch(index, Vector.empty, 1); false }
    }
  }

  def fromThrowable(phase: FailurePhase, cause: Throwable): Failure = {
    def convert(current: Throwable, depth: Int, ancestors: List[Throwable]): Failure = {
      if (ancestors.exists(_ eq current)) message(FailurePhase.Transport, "Exception cause cycle cannot be represented")
      else if (depth == ProtocolCodec.MaxFailureDepth && current.getCause != null) message(FailurePhase.Transport, "Exception cause depth exceeds the protocol limit")
      else {
        val causes = Option(current.getCause).map(next => convert(next, depth + 1, current :: ancestors)).toVector
        Failure(phase, current.getClass.getName, Option(current.getMessage).getOrElse(""), current.getStackTrace.toVector.map(_.toString), causes, None)
      }
    }
    convert(cause, 1, Nil)
  }
}
