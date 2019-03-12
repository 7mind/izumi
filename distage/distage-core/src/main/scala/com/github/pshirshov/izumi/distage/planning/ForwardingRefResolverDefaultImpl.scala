package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.planning.{ForwardingRefResolver, PlanAnalyzer}
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import distage.Id

import scala.collection.mutable

class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer,
  protected val reflectionProvider: ReflectionProvider.Runtime,
  @Id("distage.init-proxies-asap") initProxiesAsap: Boolean,
) extends ForwardingRefResolver {
  override def resolve(plan: OrderedPlan): OrderedPlan = {
    val reftable = planAnalyzer.topologyFwdRefs(plan.steps)

    lazy val usagesTable = planAnalyzer.topology(plan.steps)
    lazy val index = plan.steps.groupBy(_.target)

    val proxies = mutable.Stack[ProxyOp.MakeProxy]()

    val resolvedSteps = plan
      .toSemi
      .steps
      .collect { case i: InstantiationOp => i }
      .flatMap {
        case step if reftable.dependencies.contains(step.target) =>
          val target = step.target

          val fwdDependees = reftable.dependees.direct(target)
          val allDependees = usagesTable.dependees.direct(target)
          val followingDependees = allDependees.diff(fwdDependees)

          val onlyByNameUsages = allUsagesAreByName(index, target, followingDependees)
          val byNameAllowed = onlyByNameUsages

          val missingDeps = reftable.dependencies.direct(target)
          val op = ProxyOp.MakeProxy(step, missingDeps, step.origin, byNameAllowed)

          proxies.push(op)
          Seq(op)

        case step =>
          Seq(step)
      }

    val proxyOps = if (initProxiesAsap) {
      iniProxiesJustInTime(proxies.to, resolvedSteps)
    } else {
      initProxiesAtTheEnd(proxies.to, resolvedSteps)
    }

    val imports = plan.steps.collect({ case i: ImportDependency => i })
    OrderedPlan(plan.definition, imports ++ proxyOps, plan.roots, plan.topology)
  }

  protected def initProxiesAtTheEnd(proxies: List[ProxyOp.MakeProxy], resolvedSteps: Seq[ExecutableOp]): Seq[ExecutableOp] = {
    resolvedSteps ++ proxies.map {
      proxyDep =>
        val key = proxyDep.target
        ProxyOp.InitProxy(key, proxyDep.forwardRefs, proxyDep, proxyDep.origin)
    }
  }

    protected def iniProxiesJustInTime(proxies: mutable.HashSet[ProxyOp.MakeProxy], resolvedSteps: Seq[ExecutableOp]): Seq[ExecutableOp] = {
      // more expensive eager just-in-time policy
      val passed = mutable.HashSet[DIKey]()
      val proxyOps = resolvedSteps.flatMap {
        op =>
          passed.add(op.target)

          val completedProxies = proxies.filter(_.forwardRefs.diff(passed).isEmpty)
          val inits = completedProxies.map {
            proxy =>
              ProxyOp.InitProxy(proxy.target, proxy.forwardRefs, proxy, op.origin)
          }.toVector

          completedProxies.foreach {
            i =>
              proxies.remove(i)
          }

          op +: inits
      }
      assert(proxies.isEmpty)
      proxyOps
    }


  protected def allUsagesAreByName(index: Map[DIKey, Vector[ExecutableOp]], target: DIKey, usages: Set[DIKey]): Boolean = {
    val usedByOps = (usages + target).flatMap(index.apply)
    val associations = usedByOps.flatMap {
      case op: InstantiationOp =>
        op match {
          case ExecutableOp.CreateSet(_, _, members, _) =>
            members.map(m => m -> false)
          case _: ExecutableOp.WiringOp.ReferenceKey =>
            Seq(target -> false)
          case w: ExecutableOp.WiringOp =>
            w.wiring.associations.map(a => a.wireWith -> isByName(a))
          case w: ExecutableOp.MonadicOp =>
            w.effectWiring.associations.map(a => a.wireWith -> isByName(a))
        }
      case _: ImportDependency =>
        Seq.empty
      case _: ProxyOp =>
        Seq.empty // shouldn't happen
    }

    val onlyByNameUsages = associations.filter(_._1 == target).forall(_._2)
    onlyByNameUsages
  }

  protected def isByName(p: Association): Boolean = p match {
    case p: Association.Parameter =>
      p.isByName
    case _ =>
      false
  }
}
