package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.MakeProxy
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.planning.{ForwardingRefResolver, PlanAnalyzer}
import com.github.pshirshov.izumi.distage.model.reflection.{ReflectionProvider, SymbolIntrospector, universe}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
  , protected val symbolIntrospector: SymbolIntrospector.Runtime
  , protected val reflectionProvider: ReflectionProvider.Runtime
) extends ForwardingRefResolver {
  override def resolve(plan: OrderedPlan): OrderedPlan = {
    val reftable = planAnalyzer.topologyFwdRefs(plan.steps)

    val proxies = mutable.HashMap[DIKey, ProxyOp.MakeProxy]()

    val resolvedSteps = plan
      .steps
      .collect {
        case p: MakeProxy => p.op
        case i: InstantiationOp => i
      }
      .flatMap {
        case step if reftable.dependencies.contains(step.target) =>
          val fwd = reftable.dependencies.direct(step.target)
          val onlyByNameFwds = allForwardRefsAreByName(step.target, fwd)
          val doesNotLoopOnItself = !planAnalyzer.requirements(step).contains(step.target)
          val op = ProxyOp.MakeProxy(step, fwd, step.origin, onlyByNameFwds && doesNotLoopOnItself)

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

  protected def allForwardRefsAreByName(key: DIKey, fwd: Set[DIKey]): Boolean = {
    symbolIntrospector.hasConstructor(key.tpe) && {
      val params = reflectionProvider.constructorParameters(key.tpe)
      val forwardedParams = params.filter(p => fwd.contains(p.wireWith))
      forwardedParams.nonEmpty && forwardedParams.forall(_.isByName)
    }
  }
}
