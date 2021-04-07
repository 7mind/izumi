package izumi.distage.planning.sequential

import izumi.distage.model.definition.errors.DIError.LoopResolutionError
import izumi.distage.model.definition.errors.DIError.LoopResolutionError.BUG_UnableToFindLoop
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import izumi.distage.model.planning.ForwardingRefResolver
import izumi.distage.model.reflection._
import izumi.distage.planning.sequential.FwdrefLoopBreaker.BreakAt
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.{DG, GraphMeta}

import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable

@nowarn("msg=Unused import")
class ForwardingRefResolverDefaultImpl(
  breaker: FwdrefLoopBreaker
) extends ForwardingRefResolver {

  override def resolveMatrix(plan: DG[DIKey, ExecutableOp.SemiplanOp]): Either[List[LoopResolutionError], DG[DIKey, ExecutableOp]] = {
    val updatedPlan = mutable.HashMap.empty[DIKey, ExecutableOp]
    val updatedPredcessors = mutable.HashMap.empty[DIKey, mutable.HashSet[DIKey]]

    def register(dependee: DIKey, deps: Set[DIKey]): Unit = {
      updatedPredcessors.getOrElseUpdate(dependee, mutable.HashSet.empty) ++= deps
      ()
    }

    @tailrec
    def next(predcessors: Map[DIKey, Set[DIKey]]): Either[List[LoopResolutionError], Unit] = {
      val resolved = predcessors.filter(kv => kv._2.forall(updatedPredcessors.contains))
      if (resolved.nonEmpty) {
        resolved.foreach {
          case (r, d) =>
            register(r, d)
            assert(plan.meta.nodes.contains(r))
            updatedPlan.put(r, plan.meta.nodes(r))
        }

        val reduced = predcessors -- resolved.keySet
        next(reduced)
      } else if (predcessors.nonEmpty) {
        val loopMembers: Map[DIKey, Set[DIKey]] = predcessors.view.filterKeys(isInvolvedIntoCycle(predcessors)).toMap

        def processLoopResolution(resolution: BreakAt): Unit = {
          val dependee = resolution.dependee
          val dependencies = resolution.dependencies
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
          ()
        }

        if (loopMembers.isEmpty) {
          Left(List(BUG_UnableToFindLoop(predcessors)))
        } else {
          breaker.breakLoop(loopMembers, plan) match {
            case Left(value) =>
              Left(value)
            case Right(resolution) =>
              processLoopResolution(resolution)
              next(predcessors -- Set(resolution.dependee))
          }
        }
      } else {
        Right(())
      }
    }

    for {
      _ <- next(plan.predecessors.links)
      p = IncidenceMatrix(updatedPredcessors.view.mapValues(_.toSet).toMap)
    } yield {
      DG.fromPred(p, GraphMeta(updatedPlan.toMap))
    }
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
