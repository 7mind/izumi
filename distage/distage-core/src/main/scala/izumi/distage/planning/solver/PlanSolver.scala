package izumi.distage.planning.solver

import distage.Injector
import izumi.distage.DebugProperties
import izumi.distage.model.definition.conflicts.{Annotated, MutSel, Node}
import izumi.distage.model.definition.errors.*
import izumi.distage.model.definition.errors.ConflictResolutionError.{CannotProcessLocalContext, UnconfiguredAxisInMutators}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.plan.{ExecutableOp, Wiring}
import izumi.distage.model.planning.{ActivationChoices, AxisPoint}
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.SubcontextHandler
import izumi.distage.planning.solver.SemigraphSolver.*
import izumi.functional.IzEither.*
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.graphs.{DG, GraphMeta, WeakEdge}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.strings.IzString.*

import scala.annotation.nowarn

trait PlanSolver {
  def resolveConflicts(
    input: PlannerInput,
    planner: Planner,
  ): Either[NEList[ConflictResolutionError[DIKey, InstantiationOp]], DG[MutSel[DIKey], RemappedValue[InstantiationOp, DIKey]]]
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
              preps: GraphQueries,
  ) extends PlanSolver {

    import scala.collection.compat.*

    def resolveConflicts(
      input: PlannerInput,
      planner: Planner, // we need this for recursive planning of the local contexts
    ): Either[NEList[ConflictResolutionError[DIKey, InstantiationOp]], DG[MutSel[DIKey], RemappedValue[InstantiationOp, DIKey]]] = {

      if (enableDebugVerify) {
        val res = PlanVerifier(preps).verify[Identity](input.bindings, input.roots, Injector.providedKeys(), Set.empty)
        if (res.issues.nonEmpty) {
          System.err.println(res.issues.fromNESet.niceList())
        }
      }

      for {
        problem <- computeProblem(planner, input)
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
            predecessors = resolution.graph.predecessors.without(membersToDrop),
          )
      } yield resolved
    }

    protected def computeProblem(planner: Planner, input: PlannerInput): Either[NEList[ConflictResolutionError[DIKey, InstantiationOp]], Problem] = {
      val activations: Set[AxisPoint] = input.activation.activeChoices.map { case (a, c) => AxisPoint(a.name, c.value) }.toSet
      val ac = ActivationChoices(activations)

      for {
        allOps <- computeOperations(planner, ac, input)
        ops = preps.toDeps(allOps)
        sets <- computeSets(ac, allOps).left.map(issues => NEList(ConflictResolutionError.SetAxisProblem[DIKey](issues)))
      } yield {
        val matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp] =
          SemiEdgeSeq(ops ++ sets)

        val roots: Set[DIKey] =
          preps.getRoots(input.roots, allOps)

        val weakSetMembers: Set[WeakEdge[DIKey]] =
          preps.findWeakSetMembers(sets, preps.executableOpIndex(matrix), roots)
        Problem(activations, matrix, roots, weakSetMembers)
      }
    }

    private def computeOperations(
      planner: Planner,
      ac: ActivationChoices,
      input: PlannerInput,
    ): Either[NEList[ConflictResolutionError[DIKey, InstantiationOp]], Seq[(Annotated[DIKey], InstantiationOp)]] = {
      val handler = new SubcontextHandler.KnownActivationHandler(planner, input)

      for {
        maybeOps <- preps.computeOperationsUnsafe(handler, input.bindings).left.map(issues => NEList(CannotProcessLocalContext[DIKey](issues)))
        configuredOps = maybeOps.map {
          case aob @ (Annotated(key, Some(_), axis), _, b) =>
            isProperlyActivatedSetElement(ac, axis) {
              unconfigured =>
                Left(NEList(UnconfiguredMutatorAxis(key, b.origin, unconfigured)))
            }.map(out => (aob, out))
          case aob =>
            Right((aob, true))
        }
        out <- configuredOps.biSequence
          .map {
            value =>
              val goodMutators = value.filter(_._2).map(_._1)
              goodMutators.map {
                case (a, o, _) =>
                  (a, o)
              }.toVector
          }.left.map(issues => NEList(UnconfiguredAxisInMutators[DIKey](issues)))
      } yield {
        out
      }
    }

    private def computeSets(
      ac: ActivationChoices,
      allOps: Seq[(Annotated[DIKey], InstantiationOp)],
    ): Either[NEList[SetAxisIssue], Map[Annotated[DIKey], Node[DIKey, InstantiationOp]]] = {
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

      def handleSetWithSingleActivationSet(firstOp: CreateSet, memberKey: DIKey, ac: ActivationChoices, activations: Set[AxisPoint]) = {
        isProperlyActivatedSetElement(ac, activations) {
          unconfigured =>
            Left(NEList(SetAxisIssue.UnconfiguredSetElementAxis(firstOp.target, memberKey, firstOp.origin.value, unconfigured)))
        }.map(out => (memberKey, out))
      }

      for {
        sets <- setMembersUnsafe.map {
          case (setKey, (firstOp, membersUnsafe)) =>
            val members = membersUnsafe
              .map {
                memberKey =>
                  reverseOpIndex.get(memberKey) match {
                    case Some(other) =>
                      for {
                        configuredElements <- other.map {
                          v =>
                            handleSetWithSingleActivationSet(firstOp, memberKey, ac, v)
                              .map(isActivated => (v, isActivated))
                        }.biSequence
                        out <- configuredElements.filter(_._2._2) match {
                          case only :: Nil =>
                            Right(only._2)
                          case Nil =>
                            Right((memberKey, false))
                          case valid =>
                            Left(NEList(SetAxisIssue.InconsistentSetElementAxis(firstOp.target, memberKey, valid.map(_._1))))
                        }
                      } yield {
                        out
                      }

                    case None =>
                      Right((memberKey, true))
                  }
              }

            members.biSequence.map {
              value =>
                val goodMembers = value.view.filter(_._2).map(_._1).toSet
                val result = firstOp.copy(members = goodMembers)
                (Annotated(setKey, None, Set.empty), Node(result.members, result: InstantiationOp))
            }

        }.biSequence
      } yield {
        sets.toMap
      }
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

  private final val enableDebugVerify = DebugProperties.`izumi.distage.debug.verify-all`.boolValue(false)
}
