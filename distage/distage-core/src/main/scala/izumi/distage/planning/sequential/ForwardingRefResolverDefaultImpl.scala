package izumi.distage.planning.sequential

import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import izumi.distage.model.plan.{ExecutableOp, Roots}
import izumi.distage.model.planning.{ForwardingRefResolver, PlanAnalyzer}
import izumi.distage.model.reflection._
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.{DG, GraphMeta}

import scala.annotation.tailrec
import scala.collection.mutable

class ForwardingRefResolverDefaultImpl(
  protected val planAnalyzer: PlanAnalyzer,
) extends ForwardingRefResolver {

  override def resolveMatrix(plan: DG[DIKey, ExecutableOp.SemiplanOp], roots: Roots): DG[DIKey, ExecutableOp] = {
    val updatedPlan = mutable.HashMap.empty[DIKey, ExecutableOp]
    val updatedPredcessors = mutable.HashMap.empty[DIKey, mutable.HashSet[DIKey]]

    def register(dependee: DIKey, deps: Set[DIKey]) = {
      //println(s"reg: $dependee -> $deps")
      updatedPredcessors.getOrElseUpdate(dependee, mutable.HashSet.empty).addAll(deps)

    }
    @tailrec
    def next(predcessors: Map[DIKey, Set[DIKey]]): Unit = {
      //println(s"next on $predcessors")
      val resolved = predcessors.filter(kv => kv._2.forall(updatedPredcessors.contains))
      if (resolved.nonEmpty) {
        resolved.foreach {
          case (r, d) =>
            register(r, d)
            updatedPlan.put(r, plan.meta.nodes.getOrElse(r, ???))
        }

        val reduced = predcessors -- resolved.keySet
        next(reduced)
      } else if (predcessors.nonEmpty) {
        val loopMembers = predcessors.view.filterKeys(isInvolvedIntoCycle(predcessors)).toMap
        if (loopMembers.isEmpty) {
          ???
        }

        val (dependee, dependencies) = loopMembers.head
        val originalOp = plan.meta.nodes(dependee)
        assert(originalOp != null)
        val byNameAllowed = false
        val badDeps = dependencies.intersect(predcessors.keySet)
        val op = ProxyOp.MakeProxy(originalOp.asInstanceOf[InstantiationOp], badDeps, originalOp.origin, byNameAllowed)
        val initOpKey = DIKey.ProxyInitKey(op.target)

        val goodDeps = dependencies -- badDeps

        register(dependee, goodDeps)
        register(initOpKey, badDeps ++ Set(dependee))

        updatedPlan.put(dependee, op)
        updatedPlan.put(initOpKey, ProxyOp.InitProxy(initOpKey, badDeps, op, op.origin))

        next(predcessors -- Set(dependee))
      }
    }

    next(plan.predecessors.links)

    //println(updatedPredcessors.mkString("\n"))
    val p = IncidenceMatrix(updatedPredcessors.view.mapValues(_.toSet).toMap)
    DG.fromPred(p, GraphMeta(updatedPlan.toMap))

  }

  private[this] def isInvolvedIntoCycle[T](toPreds: Map[T, Set[T]])(key: T): Boolean = {
    test(toPreds, Set.empty, key, key)
  }

  private[this] def test[T](toPreds: Map[T, Set[T]], stack: Set[T], toTest: T, needle: T): Boolean = {
    val deps = toPreds.getOrElse(toTest, Set.empty)

    if (deps.contains(needle)) {
      true
    } else {
      deps.exists {
        d =>
          if (stack.contains(d)) {
            false
          } else {
            test(toPreds, stack + d, d, needle)
          }
      }
    }
  }
//  override def resolveMatrix(plan: DG[DIKey, ExecutableOp.SemiplanOp], roots: Roots): DG[DIKey, ExecutableOp] = {
//    val rootKeys = roots match {
//      case Roots.Of(roots) =>
//        roots.toSet
//      case Roots.Everything =>
//        plan.successors.links.toSeq.filter(_._2.isEmpty).map(_._1).toSet
//    }
//    println(s"effective roots: $rootKeys")
//    val processed = mutable.HashSet.empty[DIKey]
//    var toProcess: Set[DIKey] = rootKeys
//    val nextToProcess = mutable.HashSet.empty[DIKey]
//
//    val updatedPlan = mutable.HashMap.empty[DIKey, ExecutableOp]
//    updatedPlan.addAll(plan.meta.nodes)
//    val updatedPredcessors = mutable.HashMap.empty[DIKey, Set[DIKey]]
//    //updatedPredcessors.addAll(plan.predecessors.links)
//
//    do {
//      toProcess.foreach {
//        dependee =>
//          val dependencies = plan.predecessors.links(dependee)
//          assert(dependencies != null)
//          val badDeps = dependencies.intersect(processed)
//
//          if (badDeps.isEmpty) {
//            nextToProcess.addAll(dependencies)
//            updatedPredcessors.put(dependee, dependencies)
//          } else {

//          }
//          processed.add(dependee)
//      }
//      toProcess = nextToProcess.toSet
//      nextToProcess.clear()
//    } while (toProcess.nonEmpty)
//
//    val p = IncidenceMatrix(updatedPredcessors.toMap)
//    //println(p.links.mkString("\n"))
//    DG.fromPred(p, GraphMeta(updatedPlan.toMap))
//  }



//  override def resolve(plan: OrderedPlan): OrderedPlan = {
//    val reftable = planAnalyzer.topologyFwdRefs(plan.steps)
//
//    lazy val usagesTable = planAnalyzer.topology(plan.steps)
//    lazy val index = plan.steps.groupBy(_.target)
//
//    val proxies = mutable.Stack[ProxyOp.MakeProxy]()
//
//    val resolvedSteps = plan.toSemi.steps
//      .collect { case i: InstantiationOp => i }
//      .flatMap {
//        case step if reftable.dependencies.contains(step.target) =>
//          val target = step.target
//          val allDependees = usagesTable.dependees.direct(target)
//
//          val onlyByNameUsages = allUsagesAreByName(index, target, allDependees)
//          val byNameAllowed = onlyByNameUsages
//
//          val missingDeps = reftable.dependencies.direct(target)
//          val op = ProxyOp.MakeProxy(step, missingDeps, step.origin, byNameAllowed)
//
//          proxies.push(op)
//          Seq(op)
//
//        case step =>
//          Seq(step)
//      }
//
//    val proxyOps = if (initProxiesAsap) {
//      iniProxiesJustInTime(mutable.HashSet.newBuilder.++=(proxies).result(), resolvedSteps)
//    } else {
//      initProxiesAtTheEnd(proxies.toList, resolvedSteps)
//    }
//
//    val imports = plan.steps.collect { case i: ImportDependency => i }
//    OrderedPlan(imports ++ proxyOps, plan.declaredRoots, plan.topology)
//  }
//
//  protected def initProxiesAtTheEnd(proxies: List[ProxyOp.MakeProxy], resolvedSteps: Seq[ExecutableOp]): Seq[ExecutableOp] = {
//    resolvedSteps ++ proxies.map {
//      proxyDep =>
//        val key = proxyDep.target
//        ProxyOp.InitProxy(key, proxyDep.forwardRefs, proxyDep, proxyDep.origin)
//    }
//  }
//
//  protected def iniProxiesJustInTime(proxies: mutable.HashSet[ProxyOp.MakeProxy], resolvedSteps: Seq[ExecutableOp]): Seq[ExecutableOp] = {
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

  protected def allUsagesAreByName(index: Map[DIKey, Vector[ExecutableOp]], target: DIKey, usages: Set[DIKey]): Boolean = {
    val usedByOps = (usages + target).flatMap(index.apply)
    val associations = usedByOps.flatMap {
      case op: InstantiationOp =>
        op match {
          case ExecutableOp.CreateSet(_, members, _) =>
            members.map(m => m -> false)
          case _: ExecutableOp.WiringOp.ReferenceKey =>
            Seq(target -> false)
          case w: ExecutableOp.WiringOp =>
            w.wiring.associations.map(a => a.key -> a.isByName)
          case w: ExecutableOp.MonadicOp =>
            Seq(w.effectKey -> false)
        }
      case _: ImportDependency =>
        Seq.empty
      case _: ProxyOp =>
        Seq.empty // shouldn't happen
    }

    val onlyByNameUsages = associations.filter(_._1 == target).forall(_._2)
    onlyByNameUsages
  }

}
