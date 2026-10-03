package izumi.distage.testkit.runner.impl.services

import distage.TagK
import izumi.distage.model.{Locator, Producer}
import izumi.distage.model.plan.Plan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, Finalizer, FinalizerFilter}
import izumi.distage.testkit.runner.api.TestFinalizationReporter
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO

final class TestResourceLifecycle[F[_]: TagK](
  finalization: TestFinalizationReporter
)(implicit F: QuasiIO[F]
) {
  def produce(producer: Producer, plan: Plan): Lifecycle[F, Either[FailedProvision, Locator]] = {
    producer.produceDetailedFX[F](plan, new FinalizerFilter[F] {
      override def filter(finalizers: collection.Seq[Finalizer[F]]): collection.Seq[Finalizer[F]] = {
        finalizers.map { finalizer =>
          finalizer.copy(effect = () => F.definitelyRecoverWithTrace(F.suspendF(finalizer.effect())) { (cause, _) =>
            // Cancellation can discard release errors after this finalizer returns.
            F.flatMap(F.maybeSuspend(finalization.failure(cause)))(_ => F.fail(cause))
          })
        }
      }
    })
  }
}
