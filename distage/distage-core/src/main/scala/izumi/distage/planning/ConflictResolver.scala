package izumi.distage.planning

import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.Axis.AxisPoint
import izumi.distage.model.definition.Binding
import izumi.distage.model.definition.BindingTag.AxisTag
import izumi.distage.model.definition.conflicts.{Annotated, ConflictResolutionError, MutSel, Node}
import izumi.distage.model.exceptions._
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan.{ExecutableOp, Roots, Wiring}
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.mutations.MutationResolver._
import izumi.distage.planning.mutations.{ActivationChoices, MutationResolver}
import izumi.fundamentals.graphs.{DG, GraphMeta, WeakEdge}

import scala.annotation.nowarn

trait ConflictResolver {
  def resolveConflicts(
    input: PlannerInput
  ): Either[List[ConflictResolutionError[DIKey, InstantiationOp]], DG[MutSel[DIKey], RemappedValue[InstantiationOp, DIKey]]]
}

object ConflictResolver {
  case class Problem(
    activations: Set[AxisPoint],
    matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp],
    roots: Set[DIKey],
    weakSetMembers: Set[WeakEdge[DIKey]],
  )

  @nowarn("msg=Unused import")
  class Impl(
    bindingTranslator: BindingTranslator,
    resolver: MutationResolver[DIKey, Int, InstantiationOp],
  ) extends ConflictResolver {

    import izumi.functional.IzEither._
    import izumi.fundamentals.platform.strings.IzString._
    import scala.collection.compat._

    def resolveConflicts(
      input: PlannerInput
    ): Either[List[ConflictResolutionError[DIKey, InstantiationOp]], DG[MutSel[DIKey], RemappedValue[InstantiationOp, DIKey]]] = {
      for {
        problem <- computeProblem(input)
        resolution <- resolver.resolve(problem.matrix, problem.roots, problem.activations, problem.weakSetMembers)
        retainedKeys = resolution.graph.meta.nodes.map(_._1.key).toSet
        membersToDrop =
          resolution
            .graph.meta.nodes
            .collect {
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
          resolution
            .graph.copy(
              meta = GraphMeta(filteredWeakMembers),
              successors = resolution.graph.successors.without(membersToDrop),
              predcessors = resolution.graph.predcessors.without(membersToDrop),
            )
      } yield resolved
    }

    protected def computeProblem(input: PlannerInput): Either[Nothing, Problem] = {
      val activations: Set[AxisPoint] = input.activation.activeChoices.map { case (a, c) => AxisPoint(a.name, c.id) }.toSet
      val ac = ActivationChoices(activations)

      val allOpsMaybe = input
        .bindings.bindings.iterator
        // this is a minor optimization but it makes some conflict resolution strategies impossible
        //.filter(b => activationChoices.allValid(toAxis(b)))
        .flatMap {
          b =>
            val next = bindingTranslator.computeProvisioning(b)
            (next.provisions ++ next.sets.values).map((b, _))
        }
        .zipWithIndex
        .map {
          case ((b, n), idx) =>
            val mutIndex = b match {
              case Binding.SingletonBinding(_, _, _, _, true) =>
                Some(idx)
              case _ =>
                None
            }

            val axis = n match {
              case _: CreateSet =>
                Set.empty[AxisPoint] // actually axis marking makes no sense in case of sets
              case _ =>
                toAxis(b)
            }

            (Annotated(n.target, mutIndex, axis), n, b)
        }
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

      val ops: Vector[(Annotated[DIKey], Node[DIKey, InstantiationOp])] = allOps.collect {
        case (target, op: WiringOp) => (target, Node(op.wiring.requiredKeys, op: InstantiationOp))
        case (target, op: MonadicOp) => (target, Node(Set(op.effectKey), op: InstantiationOp))
      }

      val allSetOps = allOps
        .collect { case (target, op: CreateSet) => (target, op) }

      val reverseOpIndex: Map[DIKey, List[Set[AxisPoint]]] = allOps
        .view
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
        allSetOps
          .groupBy {
            case (a, _) =>
              assert(a.mut.isEmpty)
              assert(a.axis.isEmpty, a.toString)
              a.key
          }
          .view
          .mapValues(_.map(_._2))
          .map {
            case (setKey, ops) =>
              val firstOp = ops.head

              val members = ops
                .tail.foldLeft(ops.head.members) {
                  case (acc, op) =>
                    acc ++ op.members
                }
                .map {
                  memberKey =>
                    reverseOpIndex.get(memberKey) match {
                      case Some(value :: Nil) =>
                        isProperlyActivatedSetElement(ac, value) {
                          unconfigured =>
                            Left(
                              List(
                                UnconfiguredSetElementAxis(
                                  firstOp.target,
                                  memberKey,
                                  firstOp.origin.value,
                                  unconfigured,
                                )
                              )
                            )
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

          }
          .toMap

      val matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp] = SemiEdgeSeq(ops ++ sets)

      val roots: Set[DIKey] = input.roots match {
        case Roots.Of(roots) =>
          roots.toSet
        case Roots.Everything =>
          allOps.map(_._1.key).toSet
      }

      val weakSetMembers: Set[WeakEdge[DIKey]] = findWeakSetMembers(sets, matrix, roots)

      Right(Problem(activations, matrix, roots, weakSetMembers))
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

    protected[this] def findWeakSetMembers(
      sets: Map[Annotated[DIKey], Node[DIKey, InstantiationOp]],
      matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp],
      roots: Set[DIKey],
    ): Set[WeakEdge[DIKey]] = {
      import izumi.fundamentals.collections.IzCollections._

      val indexed = matrix
        .links.map {
          case (successor, node) =>
            (successor.key, node.meta)
        }
        .toMultimapMut

      sets
        .collect {
          case (target, Node(_, s: CreateSet)) =>
            (target, s.members)
        }
        .flatMap {
          case (_, members) =>
            members
              .diff(roots)
              .flatMap {
                member =>
                  indexed.get(member).toSeq.flatten.collect {
                    case ExecutableOp.WiringOp.ReferenceKey(_, Wiring.SingletonWiring.Reference(_, referenced, true), _) =>
                      WeakEdge(referenced, member)
                  }
              }
        }
        .toSet
    }

    protected[this] def toAxis(b: Binding): Set[AxisPoint] = {
      b.tags.collect {
        case AxisTag(axisValue) =>
          axisValue.toAxisPoint
      }
    }
  }

}
