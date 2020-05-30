package izumi.distage.planning

import scala.annotation.nowarn
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.BindingTag.AxisTag
import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.distage.model.exceptions.{ConflictResolutionException, DIBugException, SanityCheckFailedException}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan._
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.planning._
import izumi.distage.model.reflection.{DIKey, MirrorProvider}
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.gc.TracingDIGC
import izumi.functional.Value
import izumi.fundamentals.graphs.ConflictResolutionError.{AmbigiousActivationsSet, ConflictingDefs, UnsolvedConflicts}
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.MutationResolver._
import izumi.fundamentals.graphs.tools.{GC, Toposort}
import izumi.fundamentals.graphs.{ConflictResolutionError, DG, GraphMeta}
import izumi.fundamentals.platform.strings.IzString._

import scala.collection.compat._

class PlannerDefaultImpl(
  forwardingRefResolver: ForwardingRefResolver,
  sanityChecker: SanityChecker,
  gc: DIGarbageCollector,
  planningObserver: PlanningObserver,
  hook: PlanningHook,
  bindingTranslator: BindingTranslator,
  analyzer: PlanAnalyzer,
  mirrorProvider: MirrorProvider,
) extends Planner {

  override def plan(input: PlannerInput): OrderedPlan = {
    planNoRewrite(input.copy(bindings = rewrite(input.bindings)))
  }

  override def planNoRewrite(input: PlannerInput): OrderedPlan = {
    resolveConflicts(input) match {
      case Left(errors) =>
        throwOnConflict(errors)

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
          .map(order)
          .get
    }
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    hook.hookDefinition(module)
  }

  @deprecated("used in tests only!", "")
  override def finish(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(addImports)
      .eff(planningObserver.onPhase05PreGC)
      .map(gc.gc)
      .map(order)
      .get
  }

  private[distage] override def truncate(plan: OrderedPlan, roots: Set[DIKey]): OrderedPlan = {
    if (roots.isEmpty) {
      OrderedPlan.empty
    } else {
      assert(roots.diff(plan.index.keySet).isEmpty)
      val collected = new TracingDIGC(roots, plan.index, ignoreMissingDeps = false).gc(plan.steps)
      OrderedPlan(collected.nodes, roots, analyzer.topology(collected.nodes))
    }
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

  protected[this] def resolveConflicts(
    input: PlannerInput
  ): Either[List[ConflictResolutionError[DIKey, InstantiationOp]], DG[MutSel[DIKey], RemappedValue[InstantiationOp, DIKey]]] = {
    val activations: Set[AxisPoint] = input.activation.activeChoices.map { case (a, c) => AxisPoint(a.name, c.id) }.toSet
    val activationChoices = ActivationChoices(activations)

    val allOps: Vector[(Annotated[DIKey], InstantiationOp)] = input
      .bindings.bindings.iterator
      .filter(b => activationChoices.allValid(toAxis(b)))
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
          (Annotated(n.target, mutIndex, toAxis(b)), n)
      }.toVector

    val ops: Vector[(Annotated[DIKey], Node[DIKey, InstantiationOp])] = allOps.collect {
      case (target, op: WiringOp) => (target, Node(op.wiring.requiredKeys, op: InstantiationOp))
      case (target, op: MonadicOp) => (target, Node(Set(op.effectKey), op: InstantiationOp))
    }

    val sets: Map[Annotated[DIKey], Node[DIKey, InstantiationOp]] = allOps
      .collect { case (target, op: CreateSet) => (target, op) }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .mapValues {
        v =>
          val mergedSet = v.tail.foldLeft(v.head) {
            case (op, acc) =>
              acc.copy(members = acc.members ++ op.members)
          }
          val filtered = mergedSet
          Node(filtered.members, filtered: InstantiationOp)
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
      resolution <- new MutationResolverImpl[DIKey, Int, InstantiationOp]().resolve(matrix, roots, activations, weakSetMembers)
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
      .toMultimap

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

  protected[this] def order(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .eff(planningObserver.onPhase10PostGC)
      .map(hook.phase20Customization)
      .eff(planningObserver.onPhase20Customization)
      .map(hook.phase50PreForwarding)
      .eff(planningObserver.onPhase50PreForwarding)
      .map(reorderOperations)
      .map(postOrdering)
      .get
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
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.index.get(key)).map(_.origin.toSynthetic)
          missing -> ImportDependency(missing, refs, maybeFirstOrigin.getOrElse(OperationOrigin.Unknown))
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

  protected[this] def reorderOperations(completedPlan: SemiPlan): OrderedPlan = {
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

  protected[this] def postOrdering(almostPlan: OrderedPlan): OrderedPlan = {
    Value(almostPlan)
      .map(forwardingRefResolver.resolve)
      .map(hook.phase90AfterForwarding)
      .eff(planningObserver.onPhase90AfterForwarding)
      .eff(sanityChecker.assertFinalPlanSane)
      .get
  }

  protected[this] def throwOnConflict(issues: List[ConflictResolutionError[DIKey, InstantiationOp]]): Nothing = {
    val issueRepr = issues.map(formatConflict).niceList()

    throw new ConflictResolutionException(
      s"""There must be exactly one valid binding for each DIKey.
         |
         |You can use named instances: `make[X].named("id")` method and `distage.Id` annotation to disambiguate between multiple instances with the same type.
         |
         |List of problematic bindings: $issueRepr
       """.stripMargin,
      issues,
    )
  }

  protected[this] def formatConflict(conflictResolutionError: ConflictResolutionError[DIKey, InstantiationOp]): String = {
    conflictResolutionError match {
      case AmbigiousActivationsSet(issues) =>
        val printedActivationSelections = issues.map {
          case (axis, choices) => s"axis: `$axis`, selected: {${choices.map(_.value).mkString(", ")}}"
        }
        s"""Mulitple axis choices selected for axes, only one choice must be made selected for an axis:
           |
           |${printedActivationSelections.niceList().shift(4)}""".stripMargin

      case ConflictingDefs(defs) =>
        defs
          .map {
            case (k, nodes) =>
              val candidates = conflictingAxisTagsHint(
                nodes.flatMap(_._1),
                nodes.map(_._2.meta.origin).collect {
                  case defined: OperationOrigin.Defined => defined.binding
                },
              )
              s"""Conflict resolution failed key for $k with reason:
                 |
                 |${"reason dunno lmao".shift(4)}
                 |
                 |    Candidates left: ${candidates.niceList().shift(4)}""".stripMargin
          }.niceList()

      case UnsolvedConflicts(defs) =>
        defs
          .map {
            case (k, axisBinds) =>
              s"""multiple axee Conflict resolution failed key for $k with reason:
                 |
                 |${"reason axises".shift(4)}
                 |
                 |    Candidates left: ${axisBinds.niceList().shift(4)}""".stripMargin
          }.niceList()
    }
  }

  protected[this] def conflictingAxisTagsHint(activeChoices: Set[AxisPoint], ops: Set[Binding]): Seq[String] = {
    ops.toSeq.map {
      op =>
        val axisValues = op.tags.collect { case AxisTag(t) => t.toAxisPoint }

        val bindingTags = axisValues.diff(activeChoices)
        val alreadyActiveTags = axisValues.intersect(activeChoices)

        s"${op.origin}, possible: {${bindingTags.mkString(", ")}}, active: {${alreadyActiveTags.mkString(", ")}}"
    }
  }

}
