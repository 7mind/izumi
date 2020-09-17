package izumi.fundamentals.graphs.tools.mutations

import izumi.functional.IzEither._
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.graphs.ConflictResolutionError.{AmbigiousActivationsSet, ConflictingDefs, UnsolvedConflicts}
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.GC.WeakEdge
import izumi.fundamentals.graphs.tools.mutations.MutationResolver.{Annotated, AxisPoint, Resolution, SemiEdgeSeq}
import izumi.fundamentals.graphs.{ConflictResolutionError, DG, GraphMeta}

import scala.annotation.{nowarn, tailrec}

trait MutationResolver[N, I, V] {
  def resolve(
    predcessors: SemiEdgeSeq[Annotated[N], N, V],
    roots: Set[N],
    activations: Set[AxisPoint],
    weak: Set[WeakEdge[N]],
  ): Either[List[ConflictResolutionError[N, V]], Resolution[N, V]]
}

@nowarn("msg=Unused import")
object MutationResolver {
  import scala.collection.compat._
  import scala.collection.immutable

  final case class Node[N, V](deps: Set[N], meta: V)
  final case class RemappedValue[V, N](meta: V, remapped: Map[N, MutSel[N]])
  final case class SemiEdgeSeq[D, N, V](links: Seq[(D, Node[N, V])]) extends AnyVal
  final case class SemiIncidenceMatrix[D, N, V](links: Map[D, Node[N, V]]) extends AnyVal

  final case class Annotated[N](key: N, mut: Option[Int], axis: Set[AxisPoint]) {
    def withoutAxis: MutSel[N] = MutSel(key, mut)
    def isMutator: Boolean = mut.isDefined
  }
  final case class AxisPoint(axis: String, value: String) {
    override def toString: String = s"$axis:$value"
  }
  final case class Selected[N](key: N, axis: Set[AxisPoint])
  final case class MutSel[N](key: N, mut: Option[Int]) {
    def isMutator: Boolean = mut.isDefined
    def asString: String = s"$key${mut.fold("")(i => s":$i")}"
  }

  final case class Resolution[N, V](graph: DG[MutSel[N], RemappedValue[V, N]]) //, unresolved: Map[Annotated[N], Seq[Node[N, V]]])
  private final case class ResolvedMutations[N](
    lastOp: MutSel[N],
    operationReplacements: Seq[(MutSel[N], Set[MutSel[N]])],
    outerKeyReplacements: Seq[(MutSel[N], (N, MutSel[N]))],
  )

  private final case class ClassifiedConflicts[A](mutators: Set[A], defns: Set[A])
  private final case class MainResolutionStatus[N, V](resolved: SemiIncidenceMatrix[Annotated[N], N, V], unresolved: Map[Annotated[N], Seq[Node[N, V]]])
  final case class WithContext[V, N](meta: V, remaps: Map[N, MutSel[N]])

  class MutationResolverImpl[N, I, V] extends MutationResolver[N, I, V] {

    def resolve(
      predcessors: SemiEdgeSeq[Annotated[N], N, V],
      roots: Set[N],
      activations: Set[AxisPoint],
      weak: Set[WeakEdge[N]],
    ): Either[List[ConflictResolutionError[N, V]], Resolution[N, V]] = {
      for {
        semiMatrix <- resolveAxis(predcessors, roots, weak, activations)
        unsolvedConflicts = semiMatrix.links.keySet.groupBy(a => MutSel(a.key, a.mut)).filter(_._2.size > 1)
        _ <-
          if (unsolvedConflicts.nonEmpty) {
            Left(List(UnsolvedConflicts(unsolvedConflicts)))
          } else {
            Right(())
          }
        deannotated = semiMatrix.links.map {
          case (k, v) =>
            (k.withoutAxis, Node(v.deps.map(_.key), v.meta))
        }
        mutationsResolved <- resolveMutations(SemiIncidenceMatrix(deannotated))
      } yield {
        assert(semiMatrix.links.keySet.groupBy(_.withoutAxis).forall(_._2.size == 1))
        val meta = deannotated.map {
          case (k, v) =>
            val targetKey = mutationsResolved.indexRemap.getOrElse(k, k)
            val replMap = mutationsResolved.outerReplMap.getOrElse(targetKey, Map.empty)
            (targetKey, RemappedValue(v.meta, replMap))
        }

        Resolution(DG.fromPred(mutationsResolved.finalMatrix, GraphMeta(meta))) //, resolved.unresolved)
      }
    }

    protected def resolveAxis(
      predcessors: SemiEdgeSeq[Annotated[N], N, V],
      roots: Set[N],
      weak: Set[WeakEdge[N]],
      activations: Set[AxisPoint],
    ): Either[List[ConflictResolutionError[N, V]], SemiIncidenceMatrix[Annotated[N], Selected[N], V]] = {
      val activationChoices = ActivationChoices(activations)
      for {
        _ <- nonAmbigiousActivations(activations)
        (onlyValid, invalid) = predcessors.links.partition { case (k, _) => activationChoices.allValid(k.axis) }
        grouped = onlyValid.map { case (key, node) => (key.key, (key, node)) }.toMultimap
        onlyCorrect <- traceGrouped(invalid.toMultimap, activations, weak)(roots, roots, grouped, Map.empty)
      } yield {

        val nonAmbiguous = onlyCorrect.filterNot(_._1.isMutator).map { case (k, _) => (k.key, k.axis) }
        val result = onlyCorrect.map {
          case (key, node) =>
            (key, Node(node.deps.map(d => Selected(d, nonAmbiguous.getOrElse(d, Set.empty))), node.meta))
        }
        SemiIncidenceMatrix(result)
      }
    }

    protected def nonAmbigiousActivations(
      activations: Set[AxisPoint]
    ): Either[List[AmbigiousActivationsSet[N]], Unit] = {
      val bad = activations.groupBy(_.axis).filter(_._2.size > 1)
      if (bad.isEmpty)
        Right(())
      else
        Left(List(AmbigiousActivationsSet(bad)))
    }

    @tailrec
    private def traceGrouped(
      invalid: ImmutableMultiMap[Annotated[N], Node[N, V]],
      activations: Set[AxisPoint],
      weak: Set[WeakEdge[N]],
    )(roots: Set[N],
      reachable: Set[N],
      grouped: ImmutableMultiMap[N, (Annotated[N], Node[N, V])],
      currentResult: Map[Annotated[N], Node[N, V]],
    ): Either[List[ConflictResolutionError[N, V]], Map[Annotated[N], Node[N, V]]] = {
      val out: Either[List[ConflictResolutionError[N, V]], Seq[Iterable[(Annotated[N], Node[N, V])]]] = roots
        .toSeq.flatMap {
          root =>
            val node = grouped.getOrElse(root, Set.empty)
            val (mutators, definitions) = node.partition(n => n._1.isMutator)

            val resolved = NonEmptyList.from(definitions.toSeq) match {
              case Some(conflict) =>
                resolveConflict(invalid, activations)(conflict)
              case None =>
                Right(Seq.empty)
            }

            Seq(Right(mutators.toSeq), resolved)
        }
        .biAggregate

      val nxt = for {
        nextResult <- out.map(_.flatten.toMap)
        nextDeps =
          nextResult
            .toSeq
            .flatMap {
              case (successor, node) =>
                node.deps.map(d => (successor, d))
            }
            .filterNot {
              case (successor, predcessor) =>
                weak.contains(WeakEdge(predcessor, successor.key))
            }
            .map(_._2)
      } yield {
        (nextResult, nextDeps)
      }

      nxt match {
        case Left(value) =>
          Left(value)
        case Right((nextResult, nextDeps)) if nextDeps.isEmpty =>
          Right(currentResult ++ nextResult)
        case Right((nextResult, nextDeps)) =>
          traceGrouped(invalid, activations, weak)(nextDeps.toSet.diff(reachable), reachable ++ roots, grouped, currentResult ++ nextResult)
      }
    }

    protected def resolveConflict(
      @nowarn invalid: ImmutableMultiMap[Annotated[N], Node[N, V]],
      @nowarn activations: Set[AxisPoint],
    )(conflict: NonEmptyList[(Annotated[N], Node[N, V])]
    ): Either[List[ConflictResolutionError[N, V]], Map[Annotated[N], Node[N, V]]] = {
      // keep in mind: `invalid` contains elements which are known to be inactive (there is a conflicting axis point)
      conflict.size match {
        case 1 =>
          Right(Map(conflict.head))
        case _ =>
          val hasAxis = conflict.toList.filter(_._1.axis.nonEmpty)
          hasAxis match {
            case head :: Nil =>
              Right(Map(head))

            case _ =>
              Left(List(ConflictingDefs(conflict.toList.map { case (k, n) => k.withoutAxis -> (k.axis -> n) }.toMultimap)))
          }
      }
    }

    private def resolveMutations(predcessors: SemiIncidenceMatrix[MutSel[N], N, V]): Either[List[Nothing], Result] = {
      val conflicts = predcessors
        .links
        .keySet
        .groupBy(_.key)
        .filter(_._2.size > 1)

      val targets = conflicts.map {
        case (k, nn) =>
          val (mutators, definitions) = nn.partition(_.isMutator)
          (k, ClassifiedConflicts(mutators, definitions))
      }

      val resolved = targets.flatMap {
        case (t, c) =>
          doResolve(predcessors, t, c)
      }

      val allRepls = resolved.flatMap(_._2.operationReplacements)
      val finalElements = resolved.view.mapValues(_.lastOp).toMap

      val rewritten = predcessors.links.map {
        case (n, deps) =>
          val mapped = deps.deps.map {
            d =>
              val asAnn = MutSel(d, None)
              finalElements.getOrElse(asAnn, asAnn)
          }
          (n, mapped)
      }

      val result = IncidenceMatrix(rewritten ++ allRepls)
      val indexRemap: Map[MutSel[N], MutSel[N]] = finalElements
        .values
        .flatMap {
          f =>
            Seq((f, MutSel(f.key, None))) ++ result.links.keySet.filter(_.key == f.key).filterNot(_ == f).zipWithIndex.map {
              case (k, idx) =>
                (k, MutSel(k.key, Some(idx)))
            }
        }
        .toMap

      val allOuterReplacements = resolved.map(_._2.outerKeyReplacements).toSeq.flatten
      //assert(allOuterReplacements.groupBy(_._1).forall(_._2.size == 1))
      val outerReplMap = allOuterReplacements
        .toMap
        .map {
          case (k, (v, r)) =>
            (indexRemap.getOrElse(k, k), v, indexRemap.getOrElse(r, r))
        }
        .groupBy(_._1)
        .map {
          case (context, remaps) =>
            val cleaned: immutable.Iterable[(N, MutSel[N])] = remaps.map { case (_, k, t) => (k, t) }
            assert(cleaned.groupBy(_._1).forall(_._2.size < 2))
            (context, cleaned.toMap)
        }

      val finalMatrix: IncidenceMatrix[MutSel[N]] = result.rewriteAll(indexRemap)
      Right(Result(finalMatrix, indexRemap, outerReplMap))
    }

    private case class Result(
      finalMatrix: IncidenceMatrix[MutSel[N]],
      indexRemap: Map[MutSel[N], MutSel[N]],
      outerReplMap: Map[MutSel[N], Map[N, MutSel[N]]],
    )

    private def doResolve(
      predcessors: SemiIncidenceMatrix[MutSel[N], N, V],
      target: N,
      classified: ClassifiedConflicts[MutSel[N]],
    ): Option[(MutSel[N], ResolvedMutations[N])] = {
      val asFinalTarget = MutSel(target, None)

      val asSeq = classified.mutators.toSeq
      val filtered = classified.defns.filter(d => d.key == target)
      val firstOp = filtered.headOption.getOrElse(asFinalTarget)

      asSeq.headOption match {
        case Some(lastOp) =>
          // TODO: here we may sort mutators according to some rule
          val mutationsInApplicationOrder = asSeq.filterNot(_ == lastOp)
          val withFinalAtTheEnd = mutationsInApplicationOrder ++ Seq(lastOp)

          var first: MutSel[N] = firstOp
          val replacements = withFinalAtTheEnd.map {
            m =>
              val replacement = first
              first = m

              val rewritten = predcessors.links(m).deps.map {
                ld: N =>
                  if (ld == target) {
                    replacement
                  } else {
                    MutSel(ld, None)
                  }
              }

              (m, rewritten, (target, replacement))
          }

          val innerReplacements = replacements.map { case (a, _, c) => (a, c) }
          val outerReplacements = replacements.map { case (a, b, _) => (a, b) }
          Some((asFinalTarget, ResolvedMutations(lastOp, outerReplacements, innerReplacements)))
        case None =>
          None
      }
    }

  }

}
