package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, ProvisioningException}
import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.distage.model.plan.{OpFormatter, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

trait PlanInterpreter {
  // FIXME ??? allow nonmonadic
  def instantiate[F[_]: TagK: DIMonad](plan: OrderedPlan, parentContext: Locator): F[Either[FailedProvision[F], Locator]]
}

case class FailedProvision[F[_]](
                                  failed: ProvisionImmutable,
                                  plan: OrderedPlan,
                                  parentContext: Locator,
                                  failures: Seq[ProvisioningFailure],
                                  // FIXME: run deallocators
                                  deallocators: Seq[F[Unit]],
                                ) {
  // FIXME: run deallocators
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
  implicit class FailedProvisionExt[F[_]](p: Either[FailedProvision[F], Locator]) {
    def throwOnFailure(): Locator = p.fold(_.throwException(), identity)
  }
}

