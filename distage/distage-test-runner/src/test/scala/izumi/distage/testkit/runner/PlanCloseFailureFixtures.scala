package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}

private[runner] object PlanCloseFailureFixtures {
  private final class OriginalFailure(message: String) extends RuntimeException(message, null, false, true)

  def run(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector("invalid-plan", "partial-plan", "multiple-plans", "output-failure").foldLeft(Future.unit) { (before, mode) => before.flatMap { _ =>
      val identity = CatalogueIdentity(BuildId("plan-close-failure"), BuildTargetId("close-target"), CatalogueId(mode))
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val original = new OriginalFailure("original first close failure")
      val second = new OriginalFailure("original second close failure")
      val planning = new OriginalFailure("original planning failure")
      val outputFailure = new OriginalFailure("original output failure")
      val closes = new AtomicInteger(0)
      val releasing = Promise[Unit]()
      val release = Promise[Unit]()
      def suite(name: String, acquisitionFails: Boolean, closeFailure: Throwable): TestSuite = FixtureSupport.suite(name, Vector("body")) { _ =>
        new FixtureSupport.Provider {
          override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = {
            if (acquisitionFails) Future.failed(planning)
            else Future.successful(new ExecutionPlan {
              override val tests: Vector[TestDescriptor] = if (mode == "invalid-plan" || mode == "multiple-plans") Vector.empty else selected
              override val inspection: PlanInspection = PlanInspection.individualTests(selected.map(_.id))
              override def execute(execution: RunExecutionContext): Future[ProviderOutcome] = Future.failed(new IllegalStateException("Failure controls must not execute bodies"))
              override def close(): Future[Unit] = {
                val _ = closes.incrementAndGet()
                val _ = releasing.trySuccess(())
                release.future.flatMap(_ => Future.failed(closeFailure))
              }
            })
          }
        }
      }
      val factories = Vector(() => suite("OwnedFailure", false, original)) ++ (mode match {
        case "partial-plan" => Vector(() => suite("FailedAcquisition", true, second))
        case "multiple-plans" => Vector(() => suite("SecondOwnedFailure", false, second))
        case _ => Vector.empty
      })
      def protocolContains(failure: Failure, cause: Throwable): Boolean =
        (failure.exceptionClass == cause.getClass.getName && failure.message == cause.getMessage) || failure.causes.exists(protocolContains(_, cause)) || failure.suppressed.exists(protocolContains(_, cause))
      def rawContains(failure: Throwable, cause: Throwable): Boolean =
        (failure eq cause) || Option(failure.getCause).exists(rawContains(_, cause)) || failure.getSuppressed.exists(rawContains(_, cause))
      val completed = if (mode == "output-failure") {
        val command = ProtocolCodec.encode(ProtocolMessage.Request(RequestOperation.Plan, RunId(mode), request))
        val source = new ProtocolFrameSource {
          private var remaining: Option[String] = Some(command)
          override def readFrame(): Option[String] = { val result = remaining; remaining = None; result }
          override def close(): Unit = ()
        }
        val output = new ProtocolOutput { override def accept(message: ProtocolMessage): Unit = throw outputFailure }
        val operation = ApplicationLauncher.run(identity, factories, context, source, output)
        releasing.future.flatMap { _ =>
          val held = !operation.isCompleted
          val _ = release.success(())
          operation.failed.map { failure =>
            verify(held && closes.get() == 1, "Failed application output joins the held owner's close before failing")
            verify(rawContains(failure, outputFailure) && rawContains(failure, original), "Application failure retains independent output and close throwables")
          }
        }
      } else {
        val session = new RunSession(identity, factories, context, FixtureSupport.silentSink())
        val selected = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
        val operation = session.plan(selected)
        releasing.future.flatMap { _ =>
          val held = !operation.isCompleted
          val _ = release.success(())
          operation.flatMap { result =>
            val failure = result.swap.getOrElse(throw new IllegalStateException("Failure control returned a valid plan"))
            verify(held && failure.phase == FailurePhase.Planning && protocolContains(failure, original), mode + " retains planning rejection and independent held close failure")
            if (mode == "partial-plan") verify(protocolContains(failure, planning), "Partial provider rejection retains the original planning throwable")
            if (mode == "multiple-plans") verify(protocolContains(failure, second) && closes.get() == 2, "All independent provider close failures survive partial planning cleanup")
            session.close().failed.map { repeated =>
              verify(rawContains(repeated, original) && closes.get() == (if (mode == "multiple-plans") 2 else 1), mode + " repeated close joins the original failed cleanup exactly once")
            }
          }
        }
      }
      completed.map { _ =>
        verify(Vector(original, second, planning, outputFailure).forall(cause => cause.getCause == null && cause.getSuppressed.isEmpty), mode + " aggregation preserves suppression-disabled original throwables")
      }
    } }.map { _ => println("PLAN_CLOSE_FAILURE_CONTRACTS_OK cases=4") }
  }
}
