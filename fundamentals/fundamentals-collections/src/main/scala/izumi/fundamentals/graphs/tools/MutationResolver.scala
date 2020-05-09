package izumi.fundamentals.graphs.tools

import izumi.functional.IzEither._
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.graphs.ConflictResolutionError.ConflictingDefs
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.{ConflictResolutionError, DG, GraphMeta}

import scala.collection.compat._
import scala.collection.immutable

object MutationResolver {

  final case class ActivationChoices(activationChoices: Map[String, AxisPoint]) extends AnyVal {
    def validChoice(a: AxisPoint): Boolean = activationChoices.get(a.axis).forall(_ == a)
    def allValid(a: Set[AxisPoint]): Boolean = a.forall(validChoice)
  }
  object ActivationChoices {
    def apply(activations: Set[AxisPoint]): ActivationChoices = {
      new ActivationChoices(activations.map(a => (a.axis, a)).toMap)
    }
  }

  final case class Node[Idt, Meta](deps: Set[Idt], meta: Meta)
  final case class RemappedValue[Meta, Idt](meta: Meta, remapped: Map[Idt, MutSel[Idt]])
  final case class SemiEdgeSeq[D, N, V](links: Seq[(D, Node[N, V])]) extends AnyVal
  final case class SemiIncidenceMatrix[D, N, V](links: Map[D, Node[N, V]]) extends AnyVal

  final case class Annotated[N](key: N, mut: Option[Int], con: Set[AxisPoint] = Set.empty) {
    def withoutAxis: MutSel[N] = MutSel(key, mut)
    def isMutator: Boolean = mut.isDefined
  }
  final case class AxisPoint(axis: String, value: String)
  final case class Selected[N](key: N, axis: Set[AxisPoint])
  final case class MutSel[N](key: N, mut: Option[Int]) {
    def isMutator: Boolean = mut.isDefined
  }

  final case class Resolution[N, V](graph: DG[MutSel[N], RemappedValue[V, N]]) //, unresolved: Map[Annotated[N], Seq[Node[N, V]]])
  private final case class ResolvedMutations[N](
    lastOp: MutSel[N],
    operationReplacements: Seq[(MutSel[N], Set[MutSel[N]])],
    outerKeyReplacements: Seq[(MutSel[N], (N, MutSel[N]))],
  )

  private final case class ClassifiedConflicts[A](mutators: Set[A], defns: Set[A])
  private final case class MainResolutionStatus[N, V](resolved: SemiIncidenceMatrix[Annotated[N], N, V], unresolved: Map[Annotated[N], Seq[Node[N, V]]])
  final case class WithContext[Meta, N](meta: Meta, remaps: Map[N, MutSel[N]])

  class MutationResolverImpl[N, I, V] {

    def resolve(
      predcessors: SemiEdgeSeq[Annotated[N], N, V],
      roots: Set[N],
      activations: Set[AxisPoint],
    ): Either[List[ConflictResolutionError[N]], Resolution[N, V]] = {
      for {
        a <- resolveAxis(predcessors, roots, activations)
        unsolvedConflicts = a.links.keySet.groupBy(a => MutSel(a.key, a.mut)).filter(_._2.size > 1)
        _ <-
          if (unsolvedConflicts.nonEmpty) {
            Left(List(ConflictResolutionError.UnsolvedConflicts(unsolvedConflicts)))
          } else {
            Right(())
          }
        deannotated = a.links.map {
          case (k, v) =>
            (k.withoutAxis, Node(v.deps.map(_.key), v.meta))
        }
        mutationsResolved <- resolveMutations(SemiIncidenceMatrix(deannotated))
      } yield {
        assert(a.links.keySet.groupBy(_.withoutAxis).forall(_._2.size == 1))
        val meta = deannotated.map {
          case (k, v) =>
            val targetKey = mutationsResolved.indexRemap.getOrElse(k, k)
            val replMap = mutationsResolved.outerReplMap.getOrElse(targetKey, Map.empty)
            (targetKey, RemappedValue(v.meta, replMap))
        }

        Resolution(DG.fromPred(mutationsResolved.finalMatrix, GraphMeta(meta))) //, resolved.unresolved)
      }
    }

    private def resolveConflict(
      activations: Set[AxisPoint],
      conflict: NonEmptyList[(Annotated[N], Node[N, V])],
    ): Either[List[ConflictResolutionError[N]], Map[Annotated[N], Node[N, V]]] = {

      if (conflict.size == 1) {
        Right(Map(conflict.head))
      } else {
        val withoutNoAxis = conflict.toSeq.filterNot(_._1.con.isEmpty)
        if (withoutNoAxis.size == 1) {
          Right(Map(withoutNoAxis.head))
        } else {
          println(s"FAILURE/MULTI: $activations, $conflict")
          Left(List(ConflictingDefs(conflict.toSeq.toMultimap.view.mapValues(_.toSeq).toMap)))
        }

      }
    }

    private def traceGrouped(
      activations: Set[AxisPoint],
      roots: Set[N],
      reachable: Set[N],
      grouped: ImmutableMultiMap[N, (Annotated[N], Node[N, V])],
    ): Either[List[ConflictResolutionError[N]], Map[Annotated[N], Node[N, V]]] = {
      val out = roots.toSeq.flatMap {
        root =>
          val node = grouped.getOrElse(root, Set.empty)
          val (mutators, definitions) = node.partition(n => n._1.isMutator)

          val resolved = NonEmptyList.from(definitions.toSeq) match {
            case Some(value) =>
              resolveConflict(activations, value)
            case None =>
              Right(Seq.empty)
          }

          Seq(Right(mutators.toSeq), resolved)
      }

      for {
        currentResult <- out.biAggregate.map(_.flatten.toMap)
        nextDeps = currentResult.flatMap(_._2.deps)
        nextResult <-
          if (nextDeps.isEmpty) {
            Right(Map.empty)
          } else {
            traceGrouped(activations, nextDeps.toSet.diff(reachable), reachable ++ roots, grouped)
          }
      } yield {
        currentResult ++ nextResult
      }

    }

    private def resolveAxis(
      predcessors: SemiEdgeSeq[Annotated[N], N, V],
      roots: Set[N],
      activations: Set[AxisPoint],
    ): Either[List[ConflictResolutionError[N]], SemiIncidenceMatrix[Annotated[N], Selected[N], V]] = {

      for {
        activationChoices <- Right(ActivationChoices(activations))
        _ <- nonAmbigiousActivations(activations, err => ConflictResolutionError.AmbigiousActivationsSet(err))
        onlyValid = predcessors.links.filter { case (k, _) => activationChoices.allValid(k.con) }
        grouped = onlyValid.map {
          case (key, node) =>
            (key.key, (key, node))
        }.toMultimap

        onlyCorrect <- traceGrouped(activations, roots, roots, grouped)
      } yield {
        val nonAmbigious = onlyCorrect.filterNot(_._1.isMutator).map {
          case (k, _) =>
            (k.key, k.con)
        }
        val result = onlyCorrect.map {
          case (key, node) =>
            (key, Node(node.deps.map(d => Selected(d, nonAmbigious.getOrElse(d, Set.empty))), node.meta))
        }
        SemiIncidenceMatrix(result)
      }
    }

    private def nonAmbigiousActivations(
      activations: Set[AxisPoint],
      onError: Map[String, Set[AxisPoint]] => ConflictResolutionError[N],
    ): Either[List[ConflictResolutionError[N]], Unit] = {
      val bad = activations.groupBy(_.axis).filter(_._2.size > 1)
      if (bad.isEmpty)
        Right(())
      else
        Left(List(onError(bad)))
    }

    private def resolveMutations(predcessors: SemiIncidenceMatrix[MutSel[N], N, V]): Either[List[Nothing], Result] = {
      val conflicts = predcessors
        .links
        .keySet
        .groupBy(k => k.key)
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
          val withFinalAtTheEnd = asSeq.filterNot(_ == lastOp) ++ Seq(lastOp)

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
