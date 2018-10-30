package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.planning.{ForwardingRefResolver, PlanAnalyzer}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.{ReflectionProvider, SymbolIntrospector}

import scala.collection.mutable

class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
  , protected val symbolIntrospector: SymbolIntrospector.Runtime
  , protected val reflectionProvider: ReflectionProvider.Runtime
) extends ForwardingRefResolver {
  override def resolve(plan: OrderedPlan): OrderedPlan = {
    val reftable = planAnalyzer.topologyFwdRefs(plan.steps)

    val index = plan.steps.groupBy(_.target)

    val proxies = mutable.HashSet[ProxyOp.MakeProxy]()

    val resolvedSteps = plan
      .toSemi
      .steps
      .collect({ case i: InstantiationOp => i })
      .flatMap {
        case step if reftable.dependencies.contains(step.target) =>
          val target = step.target
          val fwd = reftable.dependencies.direct(target) + target

          val onlyByNameUsages = allUsagesAreByName(index, target, fwd)
          val onlyByNameFwds = allFwdConstructorParametersAreByName(target.tpe, fwd)

          val op = ProxyOp.MakeProxy(step, fwd, step.origin, onlyByNameUsages && onlyByNameFwds)

          proxies.add(op)
          Seq(op)

        case step =>
          Seq(step)
      }

    val proxyOps = addInits(proxies.toSet, resolvedSteps)

    val imports = plan.steps.collect({ case i: ImportDependency => i })
    OrderedPlan(plan.definition, imports ++ proxyOps, plan.topology)
  }

  protected def addInits(proxies: Set[ProxyOp.MakeProxy], resolvedSteps: Seq[ExecutableOp]): Seq[ExecutableOp] = {
    resolvedSteps ++ proxies.map {
      proxyDep =>
        val key = proxyDep.target
        ProxyOp.InitProxy(key, proxyDep.forwardRefs, proxyDep, proxyDep.origin)
    }
  }

  //  protected def addInitsX(proxies: mutable.HashSet[ProxyOp.MakeProxy], resolvedSteps: Seq[ExecutableOp]): Seq[ExecutableOp] = {
  //    // more expensive eager just-in-time policy
  //    val passed = mutable.HashSet[DIKey]()
  //    val proxyOps = resolvedSteps.flatMap {
  //      op =>
  //        passed.add(op.target)
  //
  //        val completedProxies = proxies.filter(_.forwardRefs.diff(passed).isEmpty)
  //        val inits = completedProxies.map {
  //          proxy =>
  //            ProxyOp.InitProxy(proxy.target, proxy.forwardRefs, proxy, op.origin)
  //        }.toVector
  //
  //        completedProxies.foreach {
  //          i =>
  //            proxies.remove(i)
  //        }
  //
  //        op +: inits
  //    }
  //    assert(proxies.isEmpty)
  //    proxyOps
  //  }


  protected def allUsagesAreByName(index: Map[DIKey, Vector[ExecutableOp]], target: DIKey, fwd: Set[DIKey]): Boolean = {
    val fwdOps = fwd.flatMap(index.apply)

    val associations = fwdOps.flatMap {
      op =>
        op match {
          case op: InstantiationOp =>
            op match {
              case ExecutableOp.CreateSet(_, _, members, _) =>
                members.map(m => m -> None)
              case op: ExecutableOp.WiringOp =>
                op.wiring.associations.map(a => a.wireWith -> Some(a))
            }
          case _: ImportDependency =>
            Seq.empty
          case _: ProxyOp =>
            Seq.empty // shouldn't happen
        }
    }

    val onlyByNameUsages = associations.filter(_._1 == target).forall(_._2.forall {
      case p: Association.Parameter =>
        p.isByName
      case _ =>
        false
    })
    onlyByNameUsages
  }

  protected def allFwdConstructorParametersAreByName(tpe: SafeType, fwd: Set[DIKey]): Boolean = {
    symbolIntrospector.hasConstructor(tpe) && {
      val params = reflectionProvider.constructorParameters(tpe)
      val forwardedParams = params.filter(p => fwd.contains(p.wireWith))
      forwardedParams.nonEmpty && forwardedParams.forall(_.isByName)
    }
  }
}
