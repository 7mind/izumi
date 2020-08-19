package izumi.distage.model.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.exceptions.{DIException, ProvisioningException}
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.plan.repr.OpFormatter
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.reflection._
import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.reflect.TagK

trait PlanInterpreter {
  def instantiate[F[_]: TagK: DIEffect](
                                         plan: OrderedPlan,
                                         parentContext: Locator,
                                         filterFinalizers: FinalizerFilter[F],
                                       ): DIResourceBase[F, Either[FailedProvision[F], Locator]]
}

object PlanInterpreter {
  trait FinalizerFilter[F[_]] {
    def filter(finalizers: collection.Seq[Finalizer[F]]): collection.Seq[Finalizer[F]]
  }
  object FinalizerFilter {
    def all[F[_]]: FinalizerFilter[F] = identity
  }

  final case class Finalizer[+F[_]](key: DIKey, effect: () => F[Unit], fType: SafeType)
  object Finalizer {
    def apply[F[_]: TagK](key: DIKey, effect: () => F[Unit]): Finalizer[F] = {
      new Finalizer(key, effect, SafeType.getK[F])
    }
  }

  final case class FailedProvision[F[_]](
                                          failed: ProvisionImmutable[F],
                                          plan: OrderedPlan,
                                          parentContext: Locator,
                                          failures: Seq[ProvisioningFailure],
                                        ) {
    def throwException[A]()(implicit F: DIEffect[F]): F[A] = {
      val repr = failures.map {
        case ProvisioningFailure(op, f) =>
          val pos = OpFormatter.formatBindingPosition(op.origin)
          val name = f match {
            case di: DIException => di.getClass.getSimpleName
            case o => o.getClass.getName
          }
          s"${op.target} $pos, $name: ${f.stackTrace}"
      }

      val ccFailed = repr.size
      val ccDone = failed.instances.size
      val ccTotal = plan.steps.size

      import izumi.fundamentals.platform.exceptions.IzThrowable._
      import izumi.fundamentals.platform.strings.IzString._

      DIEffect[F].fail {
        new ProvisioningException(s"Provisioner stopped after $ccDone instances, $ccFailed/$ccTotal operations failed: ${repr.niceList()}", null)
          .addAllSuppressed(failures.map(_.failure))
      }
    }
  }

  object FailedProvision {
    implicit final class FailedProvisionExt[F[_]](private val p: Either[FailedProvision[F], Locator]) extends AnyVal {
      def throwOnFailure()(implicit F: DIEffect[F]): F[Locator] = p.fold(_.throwException(), F.pure)
    }
  }

}

