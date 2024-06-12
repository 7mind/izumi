package izumi.distage.planning.sequential

import izumi.distage.model.definition.errors.DIError.LoopResolutionError
import izumi.distage.model.definition.errors.DIError.LoopResolutionError.NoAppropriateResolutionFound
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceKey
import izumi.distage.model.plan.ExecutableOp.{ImportOp, InstantiationOp, SemiplanOp, WiringOp}
import izumi.distage.model.reflection.{DIKey, MirrorProvider}
import izumi.distage.planning.sequential.FwdrefLoopBreaker.BreakAt
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.graphs.DG

trait FwdrefLoopBreaker {
  def breakLoop(withLoops: Map[DIKey, Set[DIKey]], plan: DG[DIKey, ExecutableOp.SemiplanOp]): Either[NEList[LoopResolutionError], BreakAt]
}

object FwdrefLoopBreaker {
  case class BreakAt(dependee: DIKey, dependencies: Set[DIKey], byNameOnly: Boolean)

  class BreakingContext(
    provider: MirrorProvider,
    withLoops: Map[DIKey, Set[DIKey]],
    plan: DG[DIKey, ExecutableOp.SemiplanOp],
  ) {
    implicit class ProxyPredicate(plan: DG[DIKey, ExecutableOp.SemiplanOp]) {
      def canBeProxied(key: DIKey): Boolean = {
        key match {
          case _: DIKey.ResourceKey | _: DIKey.EffectKey =>
            false
          case o =>
            plan.meta.nodes(o) match {
              case _: ReferenceKey =>
                false
              case _: ImportOp =>
                false
              case i: InstantiationOp =>
                i match {
                  case _: ExecutableOp.CreateSet =>
                    true
                  case _: ExecutableOp.MonadicOp =>
                    provider.canBeProxied(key.tpe)
                  case op: ExecutableOp.WiringOp =>
                    op match {
                      case _: WiringOp.CallProvider =>
                        provider.canBeProxied(key.tpe)
                      case _ =>
                        false
                    }
                }
            }
        }
      }
    }

    def breakLoop(): Either[NEList[LoopResolutionError], BreakAt] = {
      import CycleTools.*

      val candidates = withLoops.keys.toVector
      val classified = candidates.map(c => (c, plan.countByNameUsages(withLoops, c)))
      val usedByNameOnly = classified.filter(c => plan.allUsagesAreByName(withLoops, c._1))

      chooseBestBNU(usedByNameOnly)
        .map(finish(_, byName = true))
        .orElse {
          {
            val proxyable = classified.filter(c => plan.canBeProxied(c._1))
            val (withBNU, withoutBNU) = proxyable.partition(_._2 > 0)

            if (withBNU.nonEmpty) {
              chooseBestDynamicWithBNU(withBNU)
            } else {
              chooseBestDynamicNoBNU(withoutBNU.map(_._1))
            }
          }
            .map(finish(_, byName = false))
        }
        .toRight(NEList(NoAppropriateResolutionFound(candidates)))

    }

    protected def chooseBestBNU(candidates: Vector[(DIKey, Int)]): Option[DIKey] = {
      assert(candidates.forall(_._2 > 0))
      candidates.sortBy(_._2).reverse.map(_._1).headOption
    }

    protected def chooseBestDynamicWithBNU(candidates: Vector[(DIKey, Int)]): Option[DIKey] = {
      assert(candidates.exists(_._2 > 0))
      candidates.sortBy(_._2).reverse.map(_._1).headOption
    }

    protected def chooseBestDynamicNoBNU(candidates: Vector[DIKey]): Option[DIKey] = {
      candidates.sortBy(c => withLoops(c).size).reverse.headOption
    }

    protected def finish(resolution: DIKey, byName: Boolean): BreakAt = {
      assert(withLoops.contains(resolution))
      BreakAt(resolution, withLoops(resolution), byName)
    }
  }

  class FwdrefLoopBreakerDefaultImpl(
    mirrorProvider: MirrorProvider
  ) extends FwdrefLoopBreaker {
    override def breakLoop(withLoops: Map[DIKey, Set[DIKey]], plan: DG[DIKey, SemiplanOp]): Either[NEList[LoopResolutionError], BreakAt] = {
      new BreakingContext(mirrorProvider, withLoops, plan).breakLoop()
    }
  }
}
