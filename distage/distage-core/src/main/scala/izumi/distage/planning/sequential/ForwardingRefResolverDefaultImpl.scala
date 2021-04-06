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
  protected val planAnalyzer: PlanAnalyzer
) extends ForwardingRefResolver {

  override def resolveMatrix(plan: DG[DIKey, ExecutableOp.SemiplanOp], roots: Roots): DG[DIKey, ExecutableOp] = {
    val updatedPlan = mutable.HashMap.empty[DIKey, ExecutableOp]
    val updatedPredcessors = mutable.HashMap.empty[DIKey, mutable.HashSet[DIKey]]

    def register(dependee: DIKey, deps: Set[DIKey]): Unit = {
      updatedPredcessors.getOrElseUpdate(dependee, mutable.HashSet.empty).addAll(deps)
      ()
    }

    @tailrec
    def next(predcessors: Map[DIKey, Set[DIKey]]): Unit = {
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

        val onlyByNameUsages = allUsagesAreByName(plan.meta.nodes, dependee, plan.successors.links(dependee))
        val byNameAllowed = onlyByNameUsages
        
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

  private[this] def allUsagesAreByName(index: Map[DIKey, ExecutableOp.SemiplanOp], target: DIKey, usages: Set[DIKey]): Boolean = {
    val usedByOps = (usages + target).map(index.apply)
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
    }

    val onlyByNameUsages = associations.filter(_._1 == target).forall(_._2)
    onlyByNameUsages
  }

}
