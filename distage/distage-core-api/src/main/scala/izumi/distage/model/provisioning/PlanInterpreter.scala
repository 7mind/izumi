package izumi.distage.model.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiEffect
import izumi.distage.model.exceptions.{DIException, ProvisioningException}
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.plan.repr.OpFormatter
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.reflection._
import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.reflect.TagK

import scala.concurrent.duration.Duration

trait PlanInterpreter {
  def instantiate[F[_]: TagK: QuasiEffect](
    plan: OrderedPlan,
    parentContext: Locator,
    filterFinalizers: FinalizerFilter[F],
  ): Lifecycle[F, Either[FailedProvision[F], Locator]]
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

  case class FailedProvisionMeta(timings: Map[DIKey, Duration])

  final case class FailedProvision[F[_]](
    failed: ProvisionImmutable[F],
    plan: OrderedPlan,
    parentContext: Locator,
    failures: Seq[ProvisioningFailure],
    meta: FailedProvisionMeta,
    fullStackTraces: Boolean,
  ) {
    def throwException[A]()(implicit F: QuasiEffect[F]): F[A] = {
      val repr = failures.map {
        case ProvisioningFailure(op, f) =>
          val pos = OpFormatter.formatBindingPosition(op.origin)
          val name = f match {
            case di: DIException => di.getClass.getSimpleName
            case o => o.getClass.getName
          }
          val trace = if (fullStackTraces) f.stackTrace else f.getMessage
          s"${op.target} $pos, $name: $trace"
      }

      val ccFailed = repr.size
      val ccDone = failed.instances.size
      val ccTotal = plan.steps.size

      import izumi.fundamentals.platform.exceptions.IzThrowable._
      import izumi.fundamentals.platform.strings.IzString._

      QuasiEffect[F].fail {
        new ProvisioningException(s"Provisioner stopped after $ccDone instances, $ccFailed/$ccTotal operations failed:${repr.niceList()}", null)
          .addAllSuppressed(failures.map(_.failure))
      }
    }
  }

  object FailedProvision {
    implicit final class FailedProvisionExt[F[_]](private val p: Either[FailedProvision[F], Locator]) extends AnyVal {
      def throwOnFailure()(implicit F: QuasiEffect[F]): F[Locator] = p.fold(_.throwException(), F.pure)
    }
  }

}
