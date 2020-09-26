package izumi.distage.planning

import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.BindingTag.AxisTag
import izumi.distage.model.definition.{Activation, Binding, ModuleBase}
import izumi.distage.model.exceptions.{BadMutatorAxis, BadSetAxis, ConflictResolutionException, DIBugException, InconsistentSetElementAxis, SanityCheckFailedException, UnconfiguredMutatorAxis, UnconfiguredSetElementAxis}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan._
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.planning._
import izumi.distage.model.reflection.{DIKey, MirrorProvider}
import izumi.distage.model.{Planner, PlannerInput}
import izumi.functional.Value
import izumi.fundamentals.graphs.ConflictResolutionError.{AmbigiousActivationsSet, ConflictingDefs, UnsolvedConflicts}
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.mutations.MutationResolver._
import izumi.fundamentals.graphs.tools.mutations.{ActivationChoices, MutationResolver}
import izumi.fundamentals.graphs.tools.{GC, Toposort}
import izumi.fundamentals.graphs.{ConflictResolutionError, DG, GraphMeta}
import izumi.fundamentals.platform.strings.IzString._
import izumi.functional.IzEither._

import scala.annotation.nowarn

class PlannerDefaultImpl(
  forwardingRefResolver: ForwardingRefResolver,
  sanityChecker: SanityChecker,
  planningObserver: PlanningObserver,
  hook: PlanningHook,
  bindingTranslator: BindingTranslator,
  analyzer: PlanAnalyzer,
  mirrorProvider: MirrorProvider,
  resolver: MutationResolver[DIKey, Int, InstantiationOp],
) extends Planner {

  override def plan(input: PlannerInput): OrderedPlan = {
    planNoRewrite(input.copy(bindings = rewrite(input.bindings)))
  }

  override def planNoRewrite(input: PlannerInput): OrderedPlan = {
    resolveConflicts(input) match {
      case Left(errors) =>
        throwOnConflict(input.activation, errors)

      case Right(resolved) =>
        val mappedGraph = resolved.predcessors.links.toSeq.map {
          case (target, deps) =>
            val mappedTarget = updateKey(target)
            val mappedDeps = deps.map(updateKey)

            val op = resolved.meta.nodes.get(target).map {
              op =>
                val remaps = op.remapped.map {
                  case (original, remapped) =>
                    (original, updateKey(remapped))
                }
                val mapper = (key: DIKey) => remaps.getOrElse(key, key)
                op.meta.replaceKeys(key => Map(op.meta.target -> mappedTarget).getOrElse(key, key), mapper)
            }

            ((mappedTarget, mappedDeps), op.map(o => (mappedTarget, o)))
        }

        val mappedMatrix = mappedGraph.map(_._1).toMap
        val mappedOps = mappedGraph.flatMap(_._2).toMap

        val remappedKeysGraph = DG.fromPred(IncidenceMatrix(mappedMatrix), GraphMeta(mappedOps))

        // TODO: migrate semiplan to DG
        val steps = remappedKeysGraph.meta.nodes.values.toVector

        Value(SemiPlan(steps, input.roots))
          .map(addImports)
          .eff(planningObserver.onPhase10PostGC)
          .map(makeOrdered)
          .map(forwardingRefResolver.resolve)
          .eff(planningObserver.onPhase90AfterForwarding)
          .eff(sanityChecker.assertFinalPlanSane)
          .get
    }
  }

  protected[this] def makeOrdered(completedPlan: SemiPlan): OrderedPlan = {
    val topology = analyzer.topology(completedPlan.steps)

    val index = completedPlan.index

    val maybeBrokenLoops = new Toposort().cycleBreaking(
      predcessors = IncidenceMatrix(topology.dependencies.graph),
      break = new LoopBreaker(analyzer, mirrorProvider, index, topology, completedPlan),
    )

    val sortedKeys = maybeBrokenLoops match {
      case Left(value) =>
        throw new SanityCheckFailedException(s"Integrity check failed: cyclic reference not detected while it should be, $value")

      case Right(value) =>
        value
    }

    val sortedOps = sortedKeys.flatMap(index.get).toVector

    val roots = completedPlan.roots match {
      case Roots.Of(roots) =>
        roots.toSet
      case Roots.Everything =>
        topology.effectiveRoots
    }
    OrderedPlan(sortedOps, roots, topology)
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    hook.hookDefinition(module)
  }

  protected[this] def updateKey(mutSel: MutSel[DIKey]): DIKey = {
    mutSel.mut match {
      case Some(value) =>
        updateKey(mutSel.key, value)
      case None =>
        mutSel.key
    }
  }

  protected[this] def updateKey(key: DIKey, mindex: Int): DIKey = {
    key match {
      case DIKey.TypeKey(tpe, _) =>
        DIKey.TypeKey(tpe, Some(mindex))
      case k @ DIKey.IdKey(_, _, _) =>
        k.withMutatorIndex(Some(mindex))
      case s: DIKey.SetElementKey =>
        s.copy(set = updateKey(s.set, mindex))
      case r: DIKey.ResourceKey =>
        r.copy(key = updateKey(r.key, mindex))
      case e: DIKey.EffectKey =>
        e.copy(key = updateKey(e.key, mindex))
      case k =>
        throw DIBugException(s"Unexpected key mutator: $k, m=$mindex")
    }
  }

  @nowarn("msg=Unused import")
  protected[this] def resolveConflicts(
    input: PlannerInput
  ): Either[List[ConflictResolutionError[DIKey, InstantiationOp]], DG[MutSel[DIKey], RemappedValue[InstantiationOp, DIKey]]] = {
    import scala.collection.compat._

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

    val matrix = SemiEdgeSeq(ops ++ sets)

    val roots = input.roots match {
      case Roots.Of(roots) =>
        roots.toSet
      case Roots.Everything =>
        allOps.map(_._1.key).toSet
    }

    val weakSetMembers = findWeakSetMembers(sets, matrix, roots)

    for {
      resolution <- resolver.resolve(matrix, roots, activations, weakSetMembers)
      retainedKeys = resolution.graph.meta.nodes.map(_._1.key).toSet
      membersToDrop =
        resolution
          .graph.meta.nodes
          .collect {
            case (k, RemappedValue(ExecutableOp.WiringOp.ReferenceKey(_, Wiring.SingletonWiring.Reference(_, referenced, true), _), _))
                if !retainedKeys.contains(referenced) && !roots.contains(k.key) =>
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
  ): Set[GC.WeakEdge[DIKey]] = {
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
                    GC.WeakEdge(referenced, member)
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

  @nowarn("msg=Unused import")
  protected[this] def addImports(plan: SemiPlan): SemiPlan = {
    import scala.collection.compat._

    val topology = analyzer.topology(plan.steps)
    val imports = topology
      .dependees
      .graph
      .view
      .filterKeys(k => !plan.index.contains(k))
      .map {
        case (missing, refs) =>
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.index.get(key)).map(_.origin.value.toSynthetic)
          val origin = EqualizedOperationOrigin.make(maybeFirstOrigin.getOrElse(OperationOrigin.Unknown))
          (missing, ImportDependency(missing, refs, origin))
      }
      .toMap

    val allOps = (imports.values ++ plan.steps).toVector
    val missingRoots = plan.roots match {
      case Roots.Of(roots) =>
        roots
          .toSet.diff(allOps.map(_.target).toSet).map {
            root =>
              ImportDependency(root, Set.empty, OperationOrigin.Unknown)
          }.toVector
      case Roots.Everything => Vector.empty
    }
    SemiPlan(missingRoots ++ allOps, plan.roots)
  }

  protected[this] def throwOnConflict(activation: Activation, issues: List[ConflictResolutionError[DIKey, InstantiationOp]]): Nothing = {
    val issueRepr = issues.map(formatConflict(activation)).mkString("\n", "\n", "")

    throw new ConflictResolutionException(
      s"""There must be exactly one valid binding for each DIKey.
         |
         |You can use named instances: `make[X].named("id")` method and `distage.Id` annotation to disambiguate between multiple instances with the same type.
         |
         |List of problematic bindings:$issueRepr
       """.stripMargin,
      issues,
    )
  }

  protected[this] def formatConflict(activation: Activation)(conflictResolutionError: ConflictResolutionError[DIKey, InstantiationOp]): String = {
    conflictResolutionError match {
      case AmbigiousActivationsSet(issues) =>
        val printedActivationSelections = issues.map {
          case (axis, choices) => s"axis: `$axis`, selected: {${choices.map(_.value).mkString(", ")}}"
        }
        s"""Multiple axis choices selected for axes, only one choice must be made selected for an axis:
           |
           |${printedActivationSelections.niceList().shift(4)}""".stripMargin

      case ConflictingDefs(defs) =>
        defs
          .map {
            case (k, nodes) =>
              val candidates = conflictingAxisTagsHint(
                activeChoices = activation.activeChoices.values.toSet,
                ops = nodes.map(_._2.meta.origin.value),
              )
              s"""Conflict resolution failed for key `${k.asString}` with reason:
                 |
                 |   Conflicting definitions available without a disambiguating axis choice
                 |
                 |   Candidates left: ${candidates.niceList().shift(4)}""".stripMargin
          }.niceList()

      case UnsolvedConflicts(defs) =>
        defs
          .map {
            case (k, axisBinds) =>
              s"""Conflict resolution failed for key `${k.asString}` with reason:
                 |
                 |   Unsolved conflicts.
                 |
                 |   Candidates left: ${axisBinds.niceList().shift(4)}""".stripMargin
          }.niceList()
    }
  }

  protected[this] def conflictingAxisTagsHint(activeChoices: Set[AxisValue], ops: Set[OperationOrigin]): Seq[String] = {
    val axisValuesInBindings = ops
      .iterator.collect {
        case d: OperationOrigin.Defined => d.binding.tags
      }.flatten.collect {
        case AxisTag(t) => t
      }.toSet
    val alreadyActiveTags = activeChoices.intersect(axisValuesInBindings)
    ops.toSeq.map {
      op =>
        val bindingTags = op.fold(Set.empty, _.tags.collect { case AxisTag(t) => t })

        val originStr = op.fold("Unknown", _.origin.toString)
        val implTypeStr = op match {
          case OperationOrigin.SyntheticBinding(b: Binding.ImplBinding) => b.implementation.implType.toString
          case OperationOrigin.UserBinding(b: Binding.ImplBinding) => b.implementation.implType.toString
          case _ => ""
        }
        s"$implTypeStr $originStr, possible: {${bindingTags.mkString(", ")}}, active: {${alreadyActiveTags.mkString(", ")}}"
    }
  }

}
