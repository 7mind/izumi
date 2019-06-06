package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.exceptions.{SanityCheckFailedException, UnsupportedOpException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceKey
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.{GCMode, Planner, PlannerInput}
import com.github.pshirshov.izumi.functional.Value
import com.github.pshirshov.izumi.fundamentals.graphs.Toposort
import distage.DIKey

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
  symbolIntrospector: SymbolIntrospector.Runtime,
)
  extends Planner {

  override def plan(input: PlannerInput): OrderedPlan = {
    Value(prepare(input))
      .map(freeze)
      .map(finish)
      .get
  }

  override def freeze(plan: DodgyPlan): SemiPlan = {
    Value(plan)
      .map(hook.phase00PostCompletion)
      .eff(planningObserver.onPhase00PlanCompleted)
      .map(planMergingPolicy.freeze)
      .get
  }

  // TODO: add tests
  override def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan = {
    order(SemiPlan(a.definition ++ b.definition, (a.steps ++ b.steps).toVector, a.gcMode ++ b.gcMode))
  }

  override def prepare(input: PlannerInput): DodgyPlan = {
    hook
      .hookDefinition(input.bindings)
      .bindings
      .foldLeft(DodgyPlan.empty(input.bindings, input.mode)) {
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
      .filterKeys(k => !plan.index.contains(k))
      .map {
        case (missing, refs) =>
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.index.get(key)).flatMap(_.origin)
          missing -> ImportDependency(missing, refs.toSet, maybeFirstOrigin)
      }
      .toMap

    SemiPlan(plan.definition, (imports.values ++ plan.steps).toVector, plan.gcMode)
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
          val fstp = symbolIntrospector.canBeProxied(fsto.target.tpe)
          val sndp = symbolIntrospector.canBeProxied(sndo.target.tpe)

          if (fstp && !sndp) {
            true
          } else if (!fstp) {
            false
          } else if (!fsto.isInstanceOf[ReferenceKey] && sndo.isInstanceOf[ReferenceKey]) {
            true
          } else if (fsto.isInstanceOf[ReferenceKey]) {
            false
          } else {
            val fstHasByName: Boolean = hasByNameParameter(fsto)
            val sndHasByName: Boolean = hasByNameParameter(sndo)

            if (!fstHasByName && sndHasByName) {
              true
            } else if (fstHasByName && !sndHasByName) {
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
        case op: ProxyOp =>
          throw new UnsupportedOpException(s"Failed to break circular dependencies, best candidate $best is proxy O_o: $keys", op)
        case op: InstantiationOp if !symbolIntrospector.canBeProxied(op.target.tpe) =>
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

    OrderedPlan(completedPlan.definition, sortedOps.toVector, completedPlan.gcMode, topology)
  }

  private[this] def hasByNameParameter(fsto: ExecutableOp): Boolean = {
    val fstoTpe = ExecutableOp.instanceType(fsto)
    val ctorSymbol = symbolIntrospector.selectConstructorMethod(fstoTpe)
    val hasByName = ctorSymbol.exists(symbolIntrospector.hasByNameParameter)
    hasByName
  }

}
