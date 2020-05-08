package izumi.fundamentals.graphs.tools

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

  final case class Resolution[N, V](graph: DG[MutSel[N], RemappedValue[V, N]], unresolved: Map[Annotated[N], Seq[Node[N, V]]])
  private final case class ResolvedMutations[N](
    lastOp: MutSel[N],
    operationReplacements: Seq[(MutSel[N], Set[MutSel[N]])],
    outerKeyReplacements: Seq[(MutSel[N], (N, MutSel[N]))],
  )

  private final case class ClassifiedConflicts[A](mutators: Set[A], defns: Set[A])
  private final case class MainResolutionStatus[N, V](resolved: SemiIncidenceMatrix[Annotated[N], N, V], unresolved: Map[Annotated[N], Seq[Node[N, V]]])
  final case class WithContext[Meta, N](meta: Meta, remaps: Map[N, MutSel[N]])

  class MutationResolverImpl[N, I, V] {

    def resolve(predcessors: SemiEdgeSeq[Annotated[N], N, V], activations: Set[AxisPoint]): Either[List[ConflictResolutionError[N]], Resolution[N, V]] = {
      for {
        resolved <- toMap(predcessors)
        a <- resolveAxis(resolved.resolved, activations)
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
            (mutationsResolved.indexRemap.getOrElse(k, k), RemappedValue(v.meta, mutationsResolved.outerReplMap.getOrElse(k, Map.empty)))
        }

        Resolution(DG.fromPred(mutationsResolved.finalMatrix, GraphMeta(meta)), resolved.unresolved)
      }
    }

    private def toMap(predecessors: SemiEdgeSeq[Annotated[N], N, V]): Either[Nothing, MainResolutionStatus[N, V]] = {
      val grouped = predecessors
        .links
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2))
        .toMap
      val (good, bad) = grouped.partition(_._2.size == 1)
      Right(MainResolutionStatus(SemiIncidenceMatrix(good.view.mapValues(_.head).toMap), bad))
    }

    def resolveAxis(
      predcessors: SemiIncidenceMatrix[Annotated[N], N, V],
      activations: Set[AxisPoint],
    ): Either[List[ConflictResolutionError[N]], SemiIncidenceMatrix[Annotated[N], Selected[N], V]] = {
      val activationChoices = ActivationChoices(activations)

      for {
        _ <- nonAmbigiousActivations(activations, err => ConflictResolutionError.AmbigiousActivationsSet(err))
        links = predcessors.links.filter { case (k, _) => activationChoices.allValid(k.con) }
        implIndexChunks = links.keySet.toSeq.map {
          k =>
            for {
              _ <- nonAmbigiousActivations(k.con, err => ConflictResolutionError.AmbigiousActivationDefs(k, err))
            } yield
              if (k.con.intersect(activations).nonEmpty && activationChoices.allValid(k.con) && !k.isMutator)
                Option((k.key, k))
              else
                None
        }

        bad = implIndexChunks.collect { case Left(l) => l }.flatten.toList
        _ <- if (bad.nonEmpty) Left(bad) else Right(())
        good = implIndexChunks.collect { case Right(r) => r }.flatten.groupBy(_._1).view.mapValues(_.map(_._2)).toMap
        (nonAmbigiousSet, ambigious) = good.partition(_._2.size == 1)
        nonAmbigious = nonAmbigiousSet.view.mapValues(_.head).toMap
        _ <- if (ambigious.nonEmpty) Left(List(ConflictResolutionError.AmbigiousDefinitions(ambigious))) else Right(())
      } yield {
        val result: Map[Annotated[N], Node[Selected[N], V]] = links.flatMap {
          case (k, dd) =>
            val rewritten = Node(
              dd.deps.map {
                d =>
                  Selected(d, nonAmbigious.get(d).map(_.con).getOrElse(Set.empty))
              },
              dd.meta,
            )

            val nonAmbAxis = nonAmbigious.get(k.key).map(_.con).getOrElse(Set.empty)

            if (k.isMutator)
              if (activationChoices.allValid(k.con))
                Seq((Annotated(k.key, k.mut, nonAmbAxis), rewritten))
              else
                Seq.empty
            else {
              val con =
                if (k.con.isEmpty)
                  nonAmbAxis
                else
                  k.con
              Seq((Annotated(k.key, k.mut, con), rewritten))
            }

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

    def resolveMutations(predcessors: SemiIncidenceMatrix[MutSel[N], N, V]): Either[List[Nothing], Result] = {
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

    case class Result(
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
