package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, ProvisioningException}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.{OpFormatter, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, Finalizer, FinalizersFilter}
import com.github.pshirshov.izumi.distage.model.provisioning.Provision.ProvisionImmutable
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait PlanInterpreter {
  def instantiate[F[_] : TagK : DIEffect](
                                           plan: OrderedPlan
                                           , parentContext: Locator
                                           , filterFinalizers: FinalizersFilter[F]
                                         ): DIResourceBase[F, Either[FailedProvision[F], Locator]]
}

object PlanInterpreter {
  trait FinalizersFilter[F[_]] {
    def filter(finalizers: Seq[Finalizer[F]]): Seq[Finalizer[F]]
  }

  object FinalizersFilter {
    def all[F[_]]: FinalizersFilter[F] = (finalizers: Seq[Finalizer[F]]) => finalizers
  }

  case class Finalizer[+F[_]](key: DIKey, effect: () => F[Unit])

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
            case o => o.getClass.getCanonicalName
          }
          s"${op.target} $pos, $name: ${f.getMessage}"
      }

      val ccFailed = repr.size
      val ccDone = failed.instances.size
      val ccTotal = plan.steps.size

      import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
      import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._

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

