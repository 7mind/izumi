package izumi.distage.planning

import izumi.distage.model.definition.Axis.AxisChoice
import izumi.distage.model.definition.BindingTag.AxisTag
import izumi.distage.model.definition.errors.ConflictResolutionError.{ConflictingAxisChoices, ConflictingDefs, UnsolvedConflicts}
import izumi.distage.model.definition.conflicts.MutSel
import izumi.distage.model.definition.errors.{ConflictResolutionError, DIError, LoopResolutionError}
import izumi.distage.model.definition.{Activation, Binding, ModuleBase}
import izumi.distage.model.exceptions.{ConflictResolutionException, DIBugException, InjectorFailed, SanityCheckFailedException}
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, SemiplanOp}
import izumi.distage.model.plan._
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.plan.repr.KeyMinimizer
import izumi.distage.model.planning._
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.solver.{PlanSolver, SemigraphSolver}
import izumi.functional.Value
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}
import izumi.fundamentals.graphs.{DG, GraphMeta, ToposortError}
import izumi.fundamentals.platform.strings.IzString._

import scala.annotation.nowarn

class PlannerDefaultImpl(
  forwardingRefResolver: ForwardingRefResolver,
  sanityChecker: SanityChecker,
  planningObserver: PlanningObserver,
  hook: PlanningHook,
  resolver: PlanSolver,
  analyzer: PlanAnalyzer,
) extends Planner {

  override def plan(input: PlannerInput): OrderedPlan = {
    planNoRewrite(input.copy(bindings = rewrite(input.bindings)))
  }

  override def planNoRewrite(input: PlannerInput): OrderedPlan = {
    val maybePlan = for {
      resolved <- resolver.resolveConflicts(input)
      plan = preparePlan(resolved)
      withImports = addImports(plan, input.roots)
      withoutLoops <- forwardingRefResolver.resolveMatrix(withImports)
    } yield {
      // TODO: this is legacy code which just make plan DAG sequential, this needs to be removed but we have to implement DAG traversing provisioner first
      Value(withoutLoops)
        .map {
          plan =>
            val ordered = Toposort.cycleBreaking(
              predecessors = plan.predecessors,
              break = new ToposortLoopBreaker[DIKey] {
                override def onLoop(done: Seq[DIKey], loopMembers: Map[DIKey, Set[DIKey]]): Either[ToposortError[DIKey], ToposortLoopBreaker.ResolvedLoop[DIKey]] = {
                  throw new SanityCheckFailedException(s"Integrity check failed: loops are not expected at this point, processed: $done, loops: $loopMembers")
                }
              },
            )
            val topology = analyzer.topology(plan.meta.nodes.values)

            val sortedKeys = ordered match {
              case Left(value) =>
                throw new SanityCheckFailedException(s"Integrity check failed: cyclic reference not detected while it should be, $value")

              case Right(value) =>
                value
            }

            val sortedOps = sortedKeys.flatMap(plan.meta.nodes.get).toVector

            val roots = input.roots match {
              case Roots.Of(roots) =>
                roots.toSet
              case Roots.Everything =>
                topology.effectiveRoots
            }
            val finalPlan = OrderedPlan(sortedOps, roots, topology)
            finalPlan
        }
        .eff(planningObserver.onPhase90AfterForwarding)
        .eff(sanityChecker.assertFinalPlanSane)
        .get
    }

    maybePlan match {
      case Left(errors) =>
        throwOnError(input.activation, errors)

      case Right(resolved) =>
        resolved
    }
  }

  private def preparePlan(resolved: DG[MutSel[DIKey], SemigraphSolver.RemappedValue[InstantiationOp, DIKey]]) = {
    val mappedGraph = resolved.predecessors.links.toSeq.map {
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
    val plan = DG.fromPred(IncidenceMatrix(mappedMatrix), GraphMeta(mappedOps))
    plan
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

    val imports = plan.successors.links.view
      .filterKeys(k => !plan.meta.nodes.contains(k))
      .map {
        case (missing, refs) =>
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.meta.nodes.get(key)).map(_.origin.value.toSynthetic)
          val origin = EqualizedOperationOrigin(maybeFirstOrigin.getOrElse(OperationOrigin.Unknown))
          (missing, ImportDependency(missing, refs, origin))
      }
      .toMap

    val missingRoots = roots match {
      case Roots.Of(roots) =>
        roots.toSet
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

    DG.fromPred(IncidenceMatrix(plan.predecessors.links ++ allImports), fullMeta)
  }

  def formatError(e: LoopResolutionError): String = e match {
    case LoopResolutionError.BUG_UnableToFindLoop(predcessors) =>
      s"BUG: Failed to break circular dependencies, loop detector failed on matrix $predcessors which is expected to contain a loop"

    case LoopResolutionError.BUG_BestLoopResolutionIsNotSupported(op) =>
      s"BUG: Failed to break circular dependencies, best candidate ${op.target} is not a proxyable operation: $op"

    case LoopResolutionError.BestLoopResolutionCannotBeProxied(op) =>
      s"Failed to break circular dependencies, best candidate ${op.target} is not proxyable (final?): $op"
  }

  // TODO: we need to completely get rid of exceptions, this is just some transitional stuff
  protected[this] def throwOnError(activation: Activation, issues: List[DIError]): Nothing = {
    val conflicts = issues.collect { case c: ConflictResolutionError[DIKey, InstantiationOp] => c }
    if (conflicts.nonEmpty) {
      throwOnConflict(activation, conflicts)
    }
    import izumi.fundamentals.platform.strings.IzString._

    val loops = issues.collect { case e: LoopResolutionError => formatError(e) }.niceList()
    if (loops.nonEmpty) {
      throw new InjectorFailed(
        s"""Injector failed unexpectedly. List of issues: $loops
       """.stripMargin,
        issues,
      )
    }

    throw new InjectorFailed("BUG: Injector failed and is unable to provide any diagnostics", List.empty)
  }
  protected[this] def throwOnConflict(activation: Activation, issues: List[ConflictResolutionError[DIKey, InstantiationOp]]): Nothing = {
    val issueRepr = issues.map(formatConflict(activation)).mkString("\n", "\n", "")

    throw new ConflictResolutionException(
      s"""Found multiple instances for a key. There must be exactly one binding for each DIKey. List of issues:$issueRepr
         |
         |You can use named instances: `make[X].named("id")` syntax and `distage.Id` annotation to disambiguate between multiple instances of the same type.
       """.stripMargin,
      issues,
    )
  }

  protected[this] def formatConflict(activation: Activation)(conflictResolutionError: ConflictResolutionError[DIKey, InstantiationOp]): String = {
    conflictResolutionError match {
      case ConflictingAxisChoices(issues) =>
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
              conflictingAxisTagsHint(
                key = k,
                activeChoices = activation.activeChoices.values.toSet,
                ops = nodes.map(_._2.meta.origin.value),
              )
          }.niceList()

      case UnsolvedConflicts(defs) =>
        defs
          .map {
            case (k, axisBinds) =>
              s"""Conflict resolution failed for key:
                 |
                 |   - ${k.asString}
                 |
                 |   Reason: Unsolved conflicts.
                 |
                 |   Candidates left: ${axisBinds.niceList().shift(4)}""".stripMargin
          }.niceList()
    }
  }

  protected[this] def conflictingAxisTagsHint(
    key: MutSel[DIKey],
    activeChoices: Set[AxisChoice],
    ops: Set[OperationOrigin],
  ): String = {
    val keyMinimizer = KeyMinimizer(
      ops.flatMap(_.foldPartial[Set[DIKey]](Set.empty, { case b: Binding.ImplBinding => Set(DIKey.TypeKey(b.implementation.implType)) }))
      + key.key
    )
    val axisValuesInBindings = ops.iterator.collect { case d: OperationOrigin.Defined => d.binding.tags }.flatten.collect { case AxisTag(t) => t }.toSet
    val alreadyActiveTags = activeChoices.intersect(axisValuesInBindings)
    val candidates = ops.iterator
      .map {
        op =>
          val bindingTags = op.fold(Set.empty[AxisChoice], _.tags.collect { case AxisTag(t) => t })
          val conflicting = axisValuesInBindings.diff(bindingTags)
          val implTypeStr = op.foldPartial("", { case b: Binding.ImplBinding => keyMinimizer.renderType(b.implementation.implType) })
          s"$implTypeStr ${op.toSourceFilePosition} - required: {${bindingTags.mkString(", ")}}, conflicting: {${conflicting.mkString(", ")}}, active: {${alreadyActiveTags
            .mkString(", ")}}"
      }.niceList().shift(4)

    s"""Conflict resolution failed for key:
       |
       |   - ${keyMinimizer.renderKey(key.key)}
       |
       |   Reason: Conflicting definitions available without a disambiguating axis choice.
       |
       |   Candidates left:$candidates""".stripMargin
  }

}
