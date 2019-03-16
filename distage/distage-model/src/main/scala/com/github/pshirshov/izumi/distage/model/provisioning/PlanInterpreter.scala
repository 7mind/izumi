package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, ProvisioningException}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.{OpFormatter, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.provisioning.Provision.ProvisionImmutable
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

trait PlanInterpreter {
  // FIXME ??? allow nonmonadic [expose FailedProvision?]
  def instantiate[F[_]: TagK: DIEffect](plan: OrderedPlan, parentContext: Locator): DIResourceBase[F, Locator] { type InnerResource <: Either[FailedProvision[F], Locator] }
}

final case class FailedProvision[F[_]](
                                        failed: ProvisionImmutable[F],
                                        plan: OrderedPlan,
                                        parentContext: Locator,
                                        failures: Seq[ProvisioningFailure],
                                      ) {
  def throwException(): Nothing = {
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

    throw new ProvisioningException(s"Provisioner stopped after $ccDone instances, $ccFailed/$ccTotal operations failed: ${repr.niceList()}", null)
      .addAllSuppressed(failures.map(_.failure))
  }
}

object FailedProvision {
  implicit final class FailedProvisionExt[F[_]](private val p: Either[FailedProvision[F], Locator]) extends AnyVal {
    def throwOnFailure(): Locator = p.fold(_.throwException(), identity)
  }
}

