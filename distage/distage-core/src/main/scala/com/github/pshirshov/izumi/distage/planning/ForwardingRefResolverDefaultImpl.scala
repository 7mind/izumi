package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.MakeProxy
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.planning.{ForwardingRefResolver, PlanAnalyzer}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.mutable


class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
) extends ForwardingRefResolver {
  override def resolve(plan: OrderedPlan): OrderedPlan = {
    val reftable = planAnalyzer.topologyFwdRefs(plan.steps)

    val proxies = mutable.HashMap[RuntimeDIUniverse.DIKey, ProxyOp.MakeProxy]()

    val resolvedSteps = plan
      .steps
      .collect {
        case p: MakeProxy => p.op
        case i: InstantiationOp => i
      }
      .flatMap {
        case step if reftable.dependencies.contains(step.target) =>
          val op = ProxyOp.MakeProxy(step, reftable.dependencies.direct(step.target), step.origin)
          proxies += (step.target -> op)
          Seq(op)

        case step =>
          Seq(step)
      }

    val proxyOps = proxies.foldLeft(Seq.empty[ProxyOp.InitProxy]) {
      case (acc, (proxyKey, proxyDep)) =>
        acc :+ ProxyOp.InitProxy(proxyKey, proxyDep.forwardRefs, proxies(proxyKey), proxyDep.origin)
    }


    val imports = plan.steps.collect({ case i: ImportDependency => i })
    OrderedPlan(plan.definition, imports ++ resolvedSteps ++ proxyOps, plan.topology)
  }
}
