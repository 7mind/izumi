package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.MakeProxy
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import com.github.pshirshov.izumi.distage.model.plan.{FinalPlan, _}
import com.github.pshirshov.izumi.distage.model.planning.{ForwardingRefResolver, PlanAnalyzer}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.mutable


class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
) extends ForwardingRefResolver {
  override def resolve(plan: FinalPlan): FinalPlan = {
    val reftable = planAnalyzer.computeFwdRefTable(plan.steps)

    import reftable._

    val proxies = mutable.HashMap[RuntimeDIUniverse.DIKey, ProxyOp.MakeProxy]()

    val resolvedSteps = plan
      .steps
      .collect {
        case p: MakeProxy => p.op
        case i: InstantiationOp => i
      }
      .flatMap {
        case step if dependenciesOf.contains(step.target) =>
          val op = ProxyOp.MakeProxy(step, dependenciesOf(step.target))
          proxies += (step.target -> op)
          Seq(op)

        case step =>
          Seq(step)
      }

    val proxyOps = proxies.foldLeft(Seq.empty[ProxyOp.InitProxy]) {
      case (acc, (proxyKey, proxyDep)) =>
        acc :+ ProxyOp.InitProxy(proxyKey, proxyDep.forwardRefs, proxies(proxyKey))
    }


    val imports = plan.steps.collect({ case i: ImportDependency => i })
    FinalPlanImmutableImpl(plan.definition, imports ++ resolvedSteps ++ proxyOps)
  }
}
