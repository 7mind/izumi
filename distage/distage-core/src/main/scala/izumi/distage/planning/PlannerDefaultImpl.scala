package izumi.distage.planning

import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.BindingTag.AxisTag
import izumi.distage.model.definition.{Activation, Binding, ModuleBase}
import izumi.distage.model.exceptions.{ConflictResolutionException, DIBugException, SanityCheckFailedException}
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, SemiplanOp}
import izumi.distage.model.plan._
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.planning._
import izumi.distage.model.reflection.{DIKey, MirrorProvider}
import izumi.distage.model.{Planner, PlannerInput}
import izumi.functional.Value
import izumi.fundamentals.graphs.ConflictResolutionError.{AmbigiousActivationsSet, ConflictingDefs, UnsolvedConflicts}
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.Toposort
import izumi.fundamentals.graphs.tools.mutations.MutationResolver._
import izumi.fundamentals.graphs.{ConflictResolutionError, DG, GraphMeta}
import izumi.fundamentals.platform.strings.IzString._

import scala.annotation.nowarn

class PlannerDefaultImpl(
  forwardingRefResolver: ForwardingRefResolver,
  sanityChecker: SanityChecker,
  planningObserver: PlanningObserver,
  hook: PlanningHook,
  resolver: ConflictResolver,
  analyzer: PlanAnalyzer,
  mirrorProvider: MirrorProvider,
) extends Planner {

  override def plan(input: PlannerInput): OrderedPlan = {
    planNoRewrite(input.copy(bindings = rewrite(input.bindings)))
  }

  override def planNoRewrite(input: PlannerInput): OrderedPlan = {
    resolver.resolveConflicts(input) match {
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

        val mappedOps = mappedGraph.view.flatMap(_._2).toMap

        val mappedMatrix = mappedGraph.view.map(_._1).filter({ case (k, _) => mappedOps.contains(k) }).toMap

        Value(DG.fromPred(IncidenceMatrix(mappedMatrix), GraphMeta(mappedOps)))
          .map(addImports(_, input.roots))
          .map {
            dg =>
              val steps = dg.meta.nodes.values.toVector
              SemiPlan(steps, input.roots)
          }
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

    val maybeBrokenLoops = Toposort.cycleBreaking(
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
  protected[this] def addImports(plan: DG[DIKey, InstantiationOp], roots: Roots): DG[DIKey, SemiplanOp] = {
    import scala.collection.compat._

    val imports = plan
      .successors.links.view
      .filterKeys(k => !plan.meta.nodes.contains(k))
      .map {
        case (missing, refs) =>
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.meta.nodes.get(key)).map(_.origin.value.toSynthetic)
          val origin = EqualizedOperationOrigin.make(maybeFirstOrigin.getOrElse(OperationOrigin.Unknown))
          (missing, ImportDependency(missing, refs, origin))
      }
      .toMap

    val missingRoots = roots match {
      case Roots.Of(roots) =>
        roots
          .toSet
          .diff(plan.meta.nodes.keySet)
          .diff(imports.keySet)
          .map {
            root =>
              (root, ImportDependency(root, Set.empty, OperationOrigin.Unknown))
          }
          .toVector
      case Roots.Everything =>
        Vector.empty
    }

    val missingRootsImports = missingRoots.toMap

    val allImports = (imports.keySet ++ missingRootsImports.keySet).map {
      i =>
        (i, Set.empty[DIKey])
    }

    val fullMeta = GraphMeta(plan.meta.nodes ++ imports ++ missingRootsImports)

    DG.fromPred(IncidenceMatrix(plan.predcessors.links ++ allImports), fullMeta)
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
