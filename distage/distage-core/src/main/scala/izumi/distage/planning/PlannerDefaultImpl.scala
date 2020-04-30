package izumi.distage.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.exceptions.{SanityCheckFailedException, UnsupportedOpException}
import izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceKey
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, SemiplanOp}
import izumi.distage.model.plan._
import izumi.distage.model.plan.initial.PrePlan
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.planning._
import izumi.distage.model.reflection.MirrorProvider
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.gc.TracingDIGC
import izumi.functional.Value
import izumi.fundamentals.graphs.Toposort

final class PlannerDefaultImpl
(
  forwardingRefResolver: ForwardingRefResolver,
  sanityChecker: SanityChecker,
  gc: DIGarbageCollector,
  planningObserver: PlanningObserver,
  planMergingPolicy: PlanMergingPolicy,
  hook: PlanningHook,
  bindingTranslator: BindingTranslator,
  analyzer: PlanAnalyzer,
  mirrorProvider: MirrorProvider,
) extends Planner {

  override def truncate(plan: OrderedPlan, roots: Set[DIKey]): OrderedPlan = {
    if (roots.isEmpty) {
      OrderedPlan.empty
    } else {
      assert(roots.diff(plan.index.keySet).isEmpty)
      val collected = new TracingDIGC(roots, plan.index, ignoreMissingDeps = false).gc(plan.steps)
      OrderedPlan(collected.nodes, roots, analyzer.topology(collected.nodes))
    }
  }

  override def plan(input: PlannerInput): OrderedPlan = {
    Value(input)
      .map(p => p.copy(bindings = rewrite(p.bindings)))
      .map(planNoRewrite)
      .get
  }

  override def planNoRewrite(input: PlannerInput): OrderedPlan = {
    Value(input)
      .map(prepare)
      .map(freeze)
      .map(finish)
      .get
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    hook.hookDefinition(module)
  }

  override def freeze(plan: PrePlan): SemiPlan = {
    Value(plan)
      .map(hook.phase00PostCompletion)
      .eff(planningObserver.onPhase00PlanCompleted)
      .map(planMergingPolicy.freeze)
      .get
  }

  override def prepare(input: PlannerInput): PrePlan = {
    input.bindings.bindings.foldLeft(PrePlan.empty(input.bindings, input.mode)) {
      case (currentPlan, binding) =>
        Value(bindingTranslator.computeProvisioning(currentPlan, binding))
          .eff(sanityChecker.assertProvisionsSane)
          .map(next => currentPlan.append(binding, next))
          .eff(planningObserver.onSuccessfulStep)
          .get
    }
  }

  override def finish(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(addImports)
      .eff(planningObserver.onPhase05PreGC)
      .map(gc.gc)
      .map(hook.phase10PostGC)
      .eff(planningObserver.onPhase10PostGC)
      .map(hook.phase20Customization)
      .eff(planningObserver.onPhase20Customization)
      .map(order)
      .get
  }

  private[this] def order(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(hook.phase45PreForwardingCleanup)
      .map(hook.phase50PreForwarding)
      .eff(planningObserver.onPhase50PreForwarding)
      .map(reorderOperations)
      .map(forwardingRefResolver.resolve)
      .map(hook.phase90AfterForwarding)
      .eff(planningObserver.onPhase90AfterForwarding)
      .eff(sanityChecker.assertFinalPlanSane)
      .get
  }

  private[this] def addImports(plan: SemiPlan): SemiPlan = {
    val topology = analyzer.topology(plan.steps)
    val imports = topology
      .dependees
      .graph
      .view
      .filterKeys(k => !plan.index.contains(k))
      .map {
        case (missing, refs) =>
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.index.get(key)).map(_.origin.toSynthetic)
          missing -> ImportDependency(missing, refs.toSet, maybeFirstOrigin.getOrElse(OperationOrigin.Unknown))
      }
      .toMap

    val allOps = (imports.values ++ plan.steps).toVector
    val roots = plan.gcMode.toSet
    val missingRoots = roots.diff(allOps.map(_.target).toSet).map {
      root =>
        ImportDependency(root, Set.empty, OperationOrigin.Unknown)
    }.toVector

    SemiPlan(missingRoots ++ allOps, plan.gcMode)
  }

  private[this] def reorderOperations(completedPlan: SemiPlan): OrderedPlan = {
    val topology = analyzer.topology(completedPlan.steps)

    val index = completedPlan.index

    def break(keys: Set[DIKey]): DIKey = {
      val loop = keys.toList

      val best = loop.sortWith {
        case (fst, snd) =>
          val fsto = index(fst)
          val sndo = index(snd)
          val fstp = mirrorProvider.canBeProxied(fsto.target.tpe) && !effectKey(fsto.target)
          val sndp = mirrorProvider.canBeProxied(sndo.target.tpe) && !effectKey(sndo.target)

          if (fstp && !sndp) {
            true
          } else if (!fstp) {
            false
          } else if (!referenceOp(fsto) && referenceOp(sndo)) {
            true
          } else if (referenceOp(fsto)) {
            false
          } else {
            val fstHasByName: Boolean = hasByNameParameter(fsto)
            val sndHasByName: Boolean = hasByNameParameter(sndo)

            // reverse logic? prefer by-names ???
//            if (!fstHasByName && sndHasByName) {
//              true
//            } else if (fstHasByName && !sndHasByName) {
//              false
//            } else {
//              analyzer.requirements(fsto).size > analyzer.requirements(sndo).size
//            }
            if (fstHasByName && !sndHasByName) {
              true
            } else if (!fstHasByName && sndHasByName) {
              false
            } else {
              analyzer.requirements(fsto).size > analyzer.requirements(sndo).size
            }
          }
      }.head

      index(best) match {
        case op: ReferenceKey =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate $best is reference O_o: $keys", op)
        case op: ImportDependency =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate $best is import O_o: $keys", op)
        case op: InstantiationOp if !mirrorProvider.canBeProxied(op.target.tpe) && hasNonByNameUses(topology, completedPlan, op.target) =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate $best is not proxyable (final?): $keys", op)

        case _: InstantiationOp =>
          best
      }
    }

    val sortedKeys = new Toposort().cycleBreaking(
      topology.dependencies.graph
      , Seq.empty
      , break
    ) match {
      case Left(value) =>
        throw new SanityCheckFailedException(s"Integrity check failed: cyclic reference not detected while it should be, ${value.issues}")

      case Right(value) =>
        value
    }

    val sortedOps = sortedKeys.flatMap(k => index.get(k).toSeq)

    val roots = completedPlan.gcMode match {
      case GCMode.GCRoots(roots) =>
        roots
      case GCMode.NoGC =>
        topology.effectiveRoots
    }
    OrderedPlan(sortedOps.toVector, roots, topology)
  }

  private[this] def hasByNameParameter(fsto: ExecutableOp): Boolean = {
    fsto match {
      case op: ExecutableOp.WiringOp =>
        op.wiring.associations.exists(_.isByName)
      case _ =>
        false
    }
  }

  private[this] def hasNonByNameUses(topology: PlanTopology, semiPlan: SemiPlan, key: DIKey): Boolean = {
    val directDependees = topology.dependees.direct(key)
    semiPlan.steps.filter(directDependees contains _.target).exists {
      case op: ExecutableOp.WiringOp =>
        op.wiring.associations.exists(param => param.key == key && !param.isByName)
      case _ => false
    }
  }

  private[this] def effectKey(key: DIKey): Boolean = key match {
    case _: DIKey.ResourceKey | _: DIKey.EffectKey => true
    case _ => false
  }

  private[this] def referenceOp(s: SemiplanOp): Boolean = s match {
    case _: ReferenceKey /*| _: MonadicOp */=> true
    case _ => false
  }

}
