package izumi.distage.planning.sequential

import izumi.distage.model.definition.errors.DIError.LoopResolutionError
import izumi.distage.model.definition.errors.DIError.LoopResolutionError.BUG_UnableToFindLoop
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.{InstantiationOp, ProxyOp}
import izumi.distage.model.planning.ForwardingRefResolver
import izumi.distage.model.reflection.*
import izumi.distage.planning.sequential.FwdrefLoopBreaker.BreakAt
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.cycles.LoopDetector
import izumi.fundamentals.graphs.{DG, GraphMeta}

import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable

object ForwardingRefResolverDefaultImpl {

  class FwdRefResolutionContext(plan: DG[DIKey, ExecutableOp.SemiplanOp]) {
    val updatedPlan = mutable.HashMap.empty[DIKey, ExecutableOp]
    val updatedPredcessors = mutable.HashMap.empty[DIKey, mutable.HashSet[DIKey]]
    val replacements = mutable.HashMap.empty[DIKey, mutable.HashSet[(DIKey, DIKey)]]
    val knownLoops = mutable.HashMap.empty[DIKey, Set[DIKey]]

    def register(dependee: DIKey, deps: Set[DIKey]): Unit = {
      updatedPredcessors.getOrElseUpdate(dependee, mutable.HashSet.empty) ++= deps
      ()
    }

    def processLoopResolution(
      unprocessedPredecessors: Map[DIKey, Set[DIKey]],
      resolution: BreakAt,
    ): Unit = {
      /*
      Our goal is to introduce two operations, MakeProxy and InitProxy.
      All the usages outside of the referential loop will reference InitProxy by its synthetic key, thus we will maintain proper ordering
      MakeProxy will use original key and only ops involved into the loop will dereference it
       */
      val dependee = resolution.dependee
      val dependencies = resolution.dependencies
      val originalOp = plan.meta.nodes(dependee)
      assert(originalOp != null)
      assert(originalOp.target == dependee)

      val badDeps = dependencies.intersect(unprocessedPredecessors.keySet)
      val op = ProxyOp.MakeProxy(originalOp.asInstanceOf[InstantiationOp], badDeps, originalOp.origin, byNameAllowed = resolution.byNameOnly)
      val initOpKey = DIKey.ProxyInitKey(op.target)

      // this is a performance optimization which is equivalent to the following lines:
      //
      //      val loops = LoopDetector.Impl.findCyclesForNode(dependee, plan.predecessors)
      //      val loopUsers = loops.toList.flatMap(_.loops.flatMap(_.loop)).toSet
      //
      // There is another idea on how to handle the loops:
      //
      //      val loops = LoopDetector.Impl.findCyclesForNode(dependee, IncidenceMatrix(unprocessedPredecessors))
      //      val loopUsers = loops.toList.flatMap(_.loops.flatMap(_.loop)).toSet
      //
      // though it changes the way we handle nested loops
      //
      // So, now we cache first (thus broadest) loop trace though we always rely on unprocessed dependencies only,
      // which is lot more performant but equivalent to the previous logic
      //
      // TODO: it may be a good idea to explore more options here

      val loopUsers = knownLoops.get(dependee) match {
        case Some(value) =>
          value
        case None =>
          val loop = LoopDetector.Impl.findCyclesForNode(dependee, IncidenceMatrix(unprocessedPredecessors)).toList.flatMap(_.loops.flatMap(_.loop)).toSet
          loop.foreach {
            k =>
              knownLoops.put(k, loop)
          }
          loop
      }

      import CycleTools.*
      val toRewrite = plan.allDirectUsers(dependee) -- loopUsers - dependee

      toRewrite.foreach {
        k =>
          replacements.getOrElseUpdate(k, mutable.HashSet.empty) += ((dependee, initOpKey: DIKey))
      }

      val goodDeps = dependencies -- badDeps

      register(dependee, goodDeps)
      register(initOpKey, badDeps ++ Set(dependee))

      updatedPlan.put(dependee, op)
      updatedPlan.put(initOpKey, ProxyOp.InitProxy(initOpKey, badDeps, op, op.origin))
      ()
    }

  }
}

@nowarn("msg=Unused import")
class ForwardingRefResolverDefaultImpl(
  breaker: FwdrefLoopBreaker
) extends ForwardingRefResolver {

  import ForwardingRefResolverDefaultImpl.*

  import scala.collection.compat.*

  /** This solution is lot more performant and sound than it was in the early days,
    * but still there are some expensive ideas of the further improvement
    * ( see https://github.com/7mind/izumi/issues/1219 )
    *
    * There is no reason to touch this code unless we uncover some other soundness problems
    */
  override def resolveMatrix(plan: DG[DIKey, ExecutableOp.SemiplanOp]): Either[List[LoopResolutionError], DG[DIKey, ExecutableOp]] = {
    val context = new FwdRefResolutionContext(plan)

    @tailrec
    def next(predcessors: Map[DIKey, Set[DIKey]]): Either[List[LoopResolutionError], Unit] = {
      val resolved = predcessors.filter(kv => kv._2.forall(context.updatedPredcessors.contains))
      if (resolved.nonEmpty) {
        resolved.foreach {
          case (r, d) =>
            context.register(r, d)
            assert(plan.meta.nodes.contains(r))
            context.updatedPlan.put(r, plan.meta.nodes(r))
        }

        val reduced = predcessors -- resolved.keySet
        next(reduced)
      } else if (predcessors.nonEmpty) {
        val loopMembers = predcessors.view.filterKeys(isInvolvedIntoCycle(predcessors)).toMap

        if (loopMembers.isEmpty) {
          Left(List(BUG_UnableToFindLoop(predcessors)))
        } else {
          breaker.breakLoop(loopMembers, plan) match {
            case Left(value) =>
              Left(value)
            case Right(resolution) =>
              context.processLoopResolution(predcessors, resolution)
              next(predcessors -- Set(resolution.dependee))
          }
        }
      } else {
        Right(())
      }
    }

    import izumi.functional.IzEither.*
    for {
      _ <- next(plan.predecessors.links)
      _ <- context.replacements.map {
        case (in, repls) =>
          val orig = context.updatedPlan(in)
          assert(orig != null)

          val maybeIdx = repls.groupBy(_._1) // TODO: check uniq
          assert(maybeIdx.forall(_._2.size == 1))
          val idx = repls.toMap

          for {
            upd <- orig match {
              case op: InstantiationOp =>
                Right(op.replaceKeys(identity, k => idx.getOrElse(k, k)))
              case mp: ProxyOp.MakeProxy =>
                Right(mp.copy(op = mp.op.replaceKeys(identity, k => idx.getOrElse(k, k))))
              case o =>
                Left(List(LoopResolutionError.BUG_NotALoopMember(o)))
            }
          } yield {
            val preds = context.updatedPredcessors(in)
            context.updatedPredcessors.put(in, preds.map(k => idx.getOrElse(k, k)))
            context.updatedPlan.put(in, upd)
            ()
          }
      }.biAggregate
    } yield {
      val p = IncidenceMatrix(context.updatedPredcessors.view.mapValues(_.toSet).toMap)
      DG.fromPred(p, GraphMeta(context.updatedPlan.toMap))
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

}
