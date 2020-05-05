package izumi.distage.planning

import izumi.distage.model.definition.{Binding, BindingTag, ModuleBase}
import izumi.distage.model.exceptions.{ConflictResolutionException, SanityCheckFailedException, UnsupportedOpException}
import izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceKey
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, InstantiationOp, MonadicOp, SemiplanOp, WiringOp}
import izumi.distage.model.plan._
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.planning._
import izumi.distage.model.reflection.{DIKey, MirrorProvider}
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.gc.TracingDIGC
import izumi.functional.Value
import izumi.fundamentals.graphs.{ConflictResolutionError, DG}
import izumi.fundamentals.graphs.ConflictResolutionError.ConflictingDefs
import izumi.fundamentals.graphs.deprecated.Toposort
import izumi.fundamentals.graphs.tools.{GC, ToposortLoopBreaker}
import izumi.fundamentals.graphs.tools.MutationResolver._

final class PlannerDefaultImpl(
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
      case Left(value) =>
        // TODO: better formatting
        throw new ConflictResolutionException(s"Failed to resolve conflicts: $value", value)
      case Right((resolved, collected)) =>
        val steps = collected.predcessorMatrix.links.keySet.flatMap(step => resolved.meta.meta.get(step)).toVector

        /*
         *
         * */
        //      val sorted: Seq[MutSel[DIKey]] = ???
        //
        //      // meta is not garbage-collected so it may have more entries
        //      val noMutMatrix = collected.predcessorMatrix.map(_.key)
        //      val steps = sorted.map(step => resolved.meta.meta(step)).toVector
        //
        //      val topology = PlanTopology.PlanTopologyImmutable(
        //        DependencyGraph(noMutMatrix.links, DependencyKind.Depends),
        //        DependencyGraph(noMutMatrix.transposed.links, DependencyKind.Required),
        //      )
        //      OrderedPlan(steps, roots.map(_.key), topology)

        finishNoGC(SemiPlan(steps, input.mode))
    }
  }

  private def resolveConflicts(input: PlannerInput): Either[List[ConflictResolutionError[DIKey]], (DG[MutSel[DIKey], InstantiationOp], GC.GCOutput[MutSel[DIKey]])] = {
    val activations: Set[AxisPoint] = input
      .activation.activeChoices.map {
        case (a, c) =>
          AxisPoint(a.name, c.id)
      }.toSet
    val activationChoices = ActivationChoices(activations)

    val allOps: Seq[(Annotated[DIKey], InstantiationOp)] = input
      .bindings.bindings
      .filter(b => activationChoices.allValid(toAxis(b)))
      .map {
        b =>
          val next = bindingTranslator.computeProvisioning(b)
          (b, next.provisions ++ next.sets.values)
      }.toSeq.flatMap {
        case (b, nn) =>
          nn.map(n => (Annotated(n.target, None, toAxis(b)), n))
      }

    val ops: Seq[(Annotated[DIKey], Node[DIKey, InstantiationOp])] = allOps.collect {
      case (target, op: WiringOp) => (target, Node(op.wiring.requiredKeys, op: InstantiationOp))
      case (target, op: MonadicOp) => (target, Node(Set(op.effectKey), op: InstantiationOp))
    }

    val sets = allOps
      .collect {
        case (target, op: CreateSet) => (target, op)
      }.groupBy(_._1)
      .mapValues(_.map(_._2))
      .mapValues {
        v =>
          val mergedSet = v.tail.foldLeft(v.head) {
            case (op, acc) =>
              acc.copy(members = acc.members ++ op.members)
          }
          val filtered = mergedSet //.copy(members = mergedSet.members)
          Node(filtered.members, filtered: InstantiationOp)
      }

    val matrix = SemiEdgeSeq(ops ++ sets)

    for {
      resolution <- new MutationResolverImpl[DIKey, Int, InstantiationOp]().resolve(matrix, activations)
      resolved = resolution.graph
      setTargets = resolved.meta.meta.collect {
        case (target, _: CreateSet) =>
          target
      }
      weak = setTargets.flatMap {
        set =>
          val setMembers = resolved.predcessors.links(set)
          setMembers
            .filter {
              member =>
                resolved.meta.meta.get(member).exists {
                  case ExecutableOp.WiringOp.ReferenceKey(_, Wiring.SingletonWiring.Reference(_, _, weak), _) =>
                    weak
                  case _ =>
                    false
                }
            }.map(member => GC.WeakEdge(set, member))
      }.toSet
      roots = input.mode match {
        case GCMode.GCRoots(roots) =>
          roots.map(r => MutSel(r, None))
        case GCMode.NoGC =>
          resolved.predcessors.links.keySet ++ resolution.unresolved.keySet.map(_.withoutAxis)
      }
      collected <- new GC.GCTracer[MutSel[DIKey]].collect(GC.GCInput(resolved.predcessors, roots, weak)).left.map(t => List(t))
      out <- {
        val unsolved = resolution.unresolved.keySet.map(_.withoutAxis)
        val requiredButConflicting = unsolved.intersect(collected.predcessorMatrix.links.keySet)
        if (requiredButConflicting.isEmpty) {
          Right((resolved, collected))
        } else {
          Left(List(ConflictingDefs(resolution.unresolved.filter(k => requiredButConflicting.contains(k._1.withoutAxis)))))
        }
      }
    } yield {
      out
    }
  }

  private def toAxis(b: Binding): Set[AxisPoint] = {
    b.tags.collect {
      case BindingTag.AxisTag(choice) =>
        AxisPoint(choice.axis.name, choice.id)
    }
  }

  @deprecated("", "")
  override def truncate(plan: OrderedPlan, roots: Set[DIKey]): OrderedPlan = {
    if (roots.isEmpty) {
      OrderedPlan.empty
    } else {
      assert(roots.diff(plan.index.keySet).isEmpty)
      val collected = new TracingDIGC(roots, plan.index, ignoreMissingDeps = false).gc(plan.steps)
      OrderedPlan(collected.nodes, roots, analyzer.topology(collected.nodes))
    }
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    hook.hookDefinition(module)
  }

  @deprecated("", "")
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

  @deprecated("", "")
  def finishNoGC(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(addImports)
      //      .eff(planningObserver.onPhase05PreGC)
      //      .map(gc.gc)
      .map(hook.phase10PostGC)
      .eff(planningObserver.onPhase10PostGC)
      .map(hook.phase20Customization)
      .eff(planningObserver.onPhase20Customization)
      .map(order)
      .get
  }

  @deprecated("", "")
  private[this] def order(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(hook.phase45PreForwardingCleanup)
      .map(hook.phase50PreForwarding)
      .eff(planningObserver.onPhase50PreForwarding)
      .map(reorderOperations)
      .map(postOrdering)
      .get
  }

  private[this] def postOrdering(almostPlan: OrderedPlan): OrderedPlan = {
    Value(almostPlan)
      .map(forwardingRefResolver.resolve)
      .map(hook.phase90AfterForwarding)
      .eff(planningObserver.onPhase90AfterForwarding)
      .eff(sanityChecker.assertFinalPlanSane)
      .get
  }

  @deprecated("", "")
  private[this] def addImports(plan: SemiPlan): SemiPlan = {
    val topology = analyzer.topology(plan.steps)
    val imports = topology
      .dependees
      .graph
      .filterKeys(k => !plan.index.contains(k))
      .map {
        case (missing, refs) =>
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.index.get(key)).map(_.origin.toSynthetic)
          missing -> ImportDependency(missing, refs.toSet, maybeFirstOrigin.getOrElse(OperationOrigin.Unknown))
      }
      .toMap

    val allOps = (imports.values ++ plan.steps).toVector
    val roots = plan.gcMode.toSet
    val missingRoots = roots
      .diff(allOps.map(_.target).toSet).map {
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
      topology.dependencies.graph,
      Seq.empty,
      break,
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
    case _: ReferenceKey /*| _: MonadicOp */ => true
    case _ => false
  }

}
