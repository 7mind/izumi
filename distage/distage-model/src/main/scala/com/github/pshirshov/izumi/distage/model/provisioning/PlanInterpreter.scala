package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, ProvisioningException}
import com.github.pshirshov.izumi.distage.model.plan.{OpFormatter, OrderedPlan}

trait PlanInterpreter {
  def instantiate(plan: OrderedPlan, parentContext: Locator): Either[FailedProvision, Locator]
}

case class FailedProvision(
                            failed: ProvisionImmutable,
                            plan: OrderedPlan,
                            parentContext: Locator,
                            failures: Seq[ProvisioningFailure]
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
  implicit class FailedProvisionExt(p: Either[FailedProvision, Locator]) {
    def throwIfFailure(): Locator = {
      p match {
        case Left(value) =>
          value.throwException()
        case Right(value) =>
          value
      }
    }
  }
}

