package izumi.distage.planning.solver

import izumi.distage.DebugProperties
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.conflicts.{Annotated, ConflictResolutionError, MutSel, Node}
import izumi.distage.model.exceptions._
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.plan.{ExecutableOp, Wiring}
import izumi.distage.model.planning.{ActivationChoices, AxisPoint}
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.solver.SemigraphSolver._
import izumi.functional.IzEither._
import izumi.fundamentals.graphs.{DG, GraphMeta, WeakEdge}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.strings.IzString._

import scala.annotation.nowarn

trait PlanSolver {
  def resolveConflicts(
    input: PlannerInput
  ): Either[List[ConflictResolutionError[DIKey, InstantiationOp]], DG[MutSel[DIKey], RemappedValue[InstantiationOp, DIKey]]]
}

object PlanSolver {
  final case class Problem(
    activations: Set[AxisPoint],
    matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp],
    roots: Set[DIKey],
    weakSetMembers: Set[WeakEdge[DIKey]],
  )

  @nowarn("msg=Unused import")
  class Impl(
    resolver: SemigraphSolver[DIKey, Int, InstantiationOp],
    preps: GraphPreparations,
  ) extends PlanSolver {
    import scala.collection.compat._

    def resolveConflicts(
      input: PlannerInput
    ): Either[List[ConflictResolutionError[DIKey, InstantiationOp]], DG[MutSel[DIKey], RemappedValue[InstantiationOp, DIKey]]] = {

      if (enableDebugVerify) {
        val res = PlanVerifier(preps).verify[Identity](input.bindings, input.roots)
        if (res.issues.nonEmpty) {
          System.err.println(res.issues.fromNonEmptySet.niceList())
        }
      }

      for {
        problem <- computeProblem(input)
        resolution <- resolver.resolve(problem.matrix, problem.roots, problem.activations, problem.weakSetMembers)
        retainedKeys = resolution.graph.meta.nodes.map(_._1.key).toSet
        membersToDrop =
          resolution.graph.meta.nodes.collect {
            case (k, RemappedValue(ExecutableOp.WiringOp.ReferenceKey(_, Wiring.SingletonWiring.Reference(_, referenced, true), _), _))
                if !retainedKeys.contains(referenced) && !problem.roots.contains(k.key) =>
              k
          }.toSet
        keysToDrop = membersToDrop.map(_.key)
        filteredWeakMembers = resolution.graph.meta.nodes.filterNot(m => keysToDrop.contains(m._1.key)).map {
          case (k, RemappedValue(set: CreateSet, remaps)) =>
            val withoutUnreachableWeakMebers = set.members.diff(keysToDrop)
            (k, RemappedValue(set.copy(members = withoutUnreachableWeakMebers): InstantiationOp, remaps))
          case (k, o) =>
            (k, o)
        }
        resolved =
          resolution.graph.copy(
            meta = GraphMeta(filteredWeakMembers),
            successors = resolution.graph.successors.without(membersToDrop),
            predcessors = resolution.graph.predcessors.without(membersToDrop),
          )
      } yield resolved
    }

    protected def computeProblem(input: PlannerInput): Either[Nothing, Problem] = {
      val activations: Set[AxisPoint] = input.activation.activeChoices.map { case (a, c) => AxisPoint(a.name, c.value) }.toSet
      val ac = ActivationChoices(activations)

      val allOps: Seq[(Annotated[DIKey], InstantiationOp)] =
        computeOperations(ac, input)

      val ops: Seq[(Annotated[DIKey], Node[DIKey, InstantiationOp])] =
        preps.toDeps(allOps)

      val sets: Map[Annotated[DIKey], Node[DIKey, ExecutableOp.InstantiationOp]] =
        computeSets(ac, allOps)

      val matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp] =
        SemiEdgeSeq(ops ++ sets)

      val roots: Set[DIKey] =
        preps.getRoots(input.roots, allOps)

      val weakSetMembers: Set[WeakEdge[DIKey]] =
        preps.findWeakSetMembers(sets, matrix, roots)

      Right(Problem(activations, matrix, roots, weakSetMembers))
    }

    private def computeOperations(ac: ActivationChoices, input: PlannerInput): Seq[(Annotated[DIKey], InstantiationOp)] = {
      val allOpsMaybe = preps
        .computeOperationsUnsafe(input.bindings)
        .map {
          case aob @ (Annotated(key, Some(_), axis), _, b) =>
            isProperlyActivatedSetElement(ac, axis) {
              unconfigured =>
                Left(List(UnconfiguredMutatorAxis(key, b.origin, unconfigured)))
            }.map(out => (aob, out))
          case aob =>
            Right((aob, true))
        }

      val allOps: Vector[(Annotated[DIKey], InstantiationOp)] = allOpsMaybe.biAggregate match {
        case Left(value) =>
          val message = value
            .map {
              e =>
                s"Mutator for ${e.mutator} defined at ${e.pos} with unconfigured axis: ${e.unconfigured.mkString(",")}"
            }.niceList()
          throw new BadMutatorAxis(s"Mutators with unconfigured axis: $message", value)
        case Right(value) =>
          val goodMutators = value.filter(_._2).map(_._1)
          goodMutators.map {
            case (a, o, _) =>
              (a, o)
          }.toVector
      }
      allOps
    }

    private def computeSets(ac: ActivationChoices, allOps: Seq[(Annotated[DIKey], InstantiationOp)]): Map[Annotated[DIKey], Node[DIKey, InstantiationOp]] = {
      val setMembersUnsafe = preps.computeSetsUnsafe(allOps)
      val reverseOpIndex: Map[DIKey, List[Set[AxisPoint]]] = allOps.view
        .filter(_._1.mut.isEmpty)
        .map {
          case (a, _) =>
            (a.key, a.axis)
        }
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2).toList)
        .toMap

      val sets: Map[Annotated[DIKey], Node[DIKey, InstantiationOp]] =
        setMembersUnsafe.map {
          case (setKey, (firstOp, membersUnsafe)) =>
            val members = membersUnsafe
              .map {
                memberKey =>
                  reverseOpIndex.get(memberKey) match {
                    case Some(value :: Nil) =>
                      isProperlyActivatedSetElement(ac, value) {
                        unconfigured =>
                          Left(List(UnconfiguredSetElementAxis(firstOp.target, memberKey, firstOp.origin.value, unconfigured)))
                      }.map(out => (memberKey, out))
                    case Some(other) =>
                      Left(List(InconsistentSetElementAxis(firstOp.target, memberKey, other)))
                    case None =>
                      Right((memberKey, true))
                  }
              }

            members.biAggregate match {
              case Left(value) =>
                val message = value
                  .map {
                    case u: UnconfiguredSetElementAxis =>
                      s"Set ${u.set} has element ${u.element} with unconfigured axis: ${u.unconfigured.mkString(",")}"
                    case i: InconsistentSetElementAxis =>
                      s"BUG, please report at https://github.com/7mind/izumi/issues: Set ${i.set} has element with multiple axis sets: ${i.element}, unexpected axis sets: ${i.problems}"

                  }.niceList()

                throw new BadSetAxis(message, value)
              case Right(value) =>
                val goodMembers = value.filter(_._2).map(_._1)
                val result = firstOp.copy(members = goodMembers)
                (Annotated(setKey, None, Set.empty), Node(result.members, result: InstantiationOp))
            }

        }.toMap
      sets
    }

    private def isProperlyActivatedSetElement[T](ac: ActivationChoices, value: Set[AxisPoint])(onError: Set[String] => Either[T, Boolean]): Either[T, Boolean] = {
      if (ac.allValid(value)) {
        if (ac.allConfigured(value)) {
          Right(true)
        } else {
          onError(ac.findUnconfigured(value))
        }
      } else {
        Right(false)
      }
    }

  }

  private[this] final val enableDebugVerify = DebugProperties.`izumi.distage.debug.verify-all`.boolValue(false)
}
