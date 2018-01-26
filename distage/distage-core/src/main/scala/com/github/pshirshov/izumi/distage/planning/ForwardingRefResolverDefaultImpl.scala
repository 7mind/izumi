package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.DIKey
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp

import scala.collection.mutable



class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
) extends ForwardingRefResolver {
  override def resolve(plan: DodgyPlan): DodgyPlan = {
    val statements = plan.statements
    val reftable = planAnalyzer.computeFwdRefTable(statements)

    import reftable._

    val proxyInits = mutable.HashMap[DIKey, mutable.Set[DIKey]]()
    val proxies = mutable.HashMap[DIKey, ProxyOp.MakeProxy]()

    val resolvedSteps = plan.steps.flatMap {
      case step if dependencies.contains(step.target) =>
        val op = ProxyOp.MakeProxy(step, dependencies(step.target))
        proxies += (step.target -> op)
        Seq(op)

      case step if dependants.contains(step.target) =>
        dependants(step.target).foreach {
          proxy =>
            proxyInits.getOrElseUpdate(proxy, mutable.Set.empty) += step.target
        }

        Seq(step)

      case step =>
        Seq(step)
    }

    val proxyOps = proxyInits.foldLeft(Seq.empty[ProxyOp.InitProxy]) {
      case (acc, (proxyKey, proxyDep)) =>
        acc :+ ProxyOp.InitProxy(proxyKey, proxyDep.toSet, proxies(proxyKey))
    }

    plan.copy(steps = resolvedSteps, proxies  = proxyOps)
  }
}
