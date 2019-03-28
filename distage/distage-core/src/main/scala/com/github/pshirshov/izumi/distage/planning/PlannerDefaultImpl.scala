package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.{Planner, PlannerInput}
import com.github.pshirshov.izumi.functional.Value

class PlannerDefaultImpl
(
  protected val forwardingRefResolver: ForwardingRefResolver
, protected val reflectionProvider: ReflectionProvider.Runtime
, protected val sanityChecker: SanityChecker
, protected val gc: DIGarbageCollector
, protected val planningObservers: Set[PlanningObserver]
, protected val planMergingPolicy: PlanMergingPolicy
, protected val planningHooks: Set[PlanningHook]
)
  extends Planner {

  private[this] val hook = new PlanningHookAggregate(planningHooks)
  private[this] val planningObserver = new PlanningObserverAggregate(planningObservers)

  override def plan(input: PlannerInput): OrderedPlan = {
    val plan = hook.hookDefinition(input.bindings).bindings.foldLeft(DodgyPlan.empty(input.bindings, input.roots)) {
      case (currentPlan, binding) =>
        Value(computeProvisioning(currentPlan, binding))
          .eff(sanityChecker.assertProvisionsSane)
          .map(planMergingPolicy.extendPlan(currentPlan, binding, _))
          .eff(sanityChecker.assertStepSane)
          .map(hook.hookStep(input.bindings, currentPlan, binding, _))
          .eff(planningObserver.onSuccessfulStep)
          .get
    }

    Value(plan)
      .map(hook.phase00PostCompletion)
      .eff(planningObserver.onPhase00PlanCompleted)
      .map(planMergingPolicy.finalizePlan)
      .map(finish)
      .get
  }

  def finish(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(planMergingPolicy.addImports)
      .eff(planningObserver.onPhase05PreGC)
      .map(doGC)
      .map(hook.phase10PostGC)
      .eff(planningObserver.onPhase10PostGC)
      .map(hook.phase20Customization)
      .eff(planningObserver.onPhase20Customization)
      .map(order)
      .get
  }

  // TODO: add tests
  override def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan = {
    order(SemiPlan(a.definition ++ b.definition, (a.steps ++ b.steps).toVector, a.roots ++ b.roots))
  }

  private[this] def doGC(semiPlan: SemiPlan): SemiPlan = {
    if (semiPlan.roots.nonEmpty) {
      gc.gc(semiPlan)
    } else {
      semiPlan
    }
  }

  private[this] def order(semiPlan: SemiPlan): OrderedPlan = {
    Value(semiPlan)
      .map(hook.phase45PreForwardingCleanup)
      .map(hook.phase50PreForwarding)
      .eff(planningObserver.onPhase50PreForwarding)
      .map(planMergingPolicy.reorderOperations)
      .map(forwardingRefResolver.resolve)
      .map(hook.phase90AfterForwarding)
      .eff(planningObserver.onPhase90AfterForwarding)
      .eff(sanityChecker.assertFinalPlanSane)
      .get
  }

  private[this] def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): NextOps = {
    binding match {
      case singleton: SingletonBinding[_] =>
        val newOp = provisionSingleton(singleton)

        NextOps(
          sets = Map.empty
        , provisions = Seq(newOp)
        )

      case set: SetElementBinding[_] =>
        val target = set.key

        val discriminator = setElementDiscriminatorKey(set, currentPlan)
        val elementKey = DIKey.SetElementKey(target, discriminator)
        val next = computeProvisioning(currentPlan, SingletonBinding(elementKey, set.implementation, set.tags, set.origin))
        val oldSet = next.sets.getOrElse(target, CreateSet(target, target.tpe, Set.empty, Some(binding)))
        val newSet = oldSet.copy(members = oldSet.members + elementKey)

        NextOps(
          sets = next.sets.updated(target, newSet)
        , provisions = next.provisions
        )

      case set: EmptySetBinding[_] =>
        val newSet = CreateSet(set.key, set.key.tpe, Set.empty, Some(binding))

        NextOps(
          sets = Map(set.key -> newSet)
        , provisions = Seq.empty
        )
    }
  }

  private[this] def provisionSingleton(binding: Binding.ImplBinding): InstantiationOp = {
    val target = binding.key
    val wiring0 = implToWiring(binding.implementation)
    val wiring = hook.hookWiring(binding, wiring0)

    wiringToInstantiationOp(target, binding, wiring)
  }

  private[this] def wiringToInstantiationOp(target: DIKey, binding: Binding, wiring: Wiring): InstantiationOp = {
    wiring match {
      case w: PureWiring =>
        pureWiringToWiringOp(target, binding, w)

      case w: MonadicWiring.Effect =>
        MonadicOp.ExecuteEffect(target, pureWiringToWiringOp(w.effectDIKey, binding, w.effectWiring), w, Some(binding))

      case w: MonadicWiring.Resource =>
        MonadicOp.AllocateResource(target, pureWiringToWiringOp(w.effectDIKey, binding, w.effectWiring), w, Some(binding))
    }
  }

  private[this] def pureWiringToWiringOp(target: DIKey, binding: Binding, wiring: PureWiring): WiringOp = {
    wiring match {
      case w: Constructor =>
        WiringOp.InstantiateClass(target, w, Some(binding))

      case w: AbstractSymbol =>
        WiringOp.InstantiateTrait(target, w, Some(binding))

      case w: Factory =>
        WiringOp.InstantiateFactory(target, w, Some(binding))

      case w: FactoryFunction =>
        WiringOp.CallFactoryProvider(target, w, Some(binding))

      case w: Function =>
        WiringOp.CallProvider(target, w, Some(binding))

      case w: Instance =>
        WiringOp.ReferenceInstance(target, w, Some(binding))

      case w: Reference =>
        WiringOp.ReferenceKey(target, w, Some(binding))
    }
  }

  private[this] def implToWiring(implementation: ImplDef): Wiring = {
    implementation match {
      case d: ImplDef.DirectImplDef =>
        directImplToPureWiring(d)
      case e: ImplDef.EffectImpl =>
        MonadicWiring.Effect(e.implType, e.effectHKTypeCtor, directImplToPureWiring(e.effectImpl))
      case r: ImplDef.ResourceImpl =>
        MonadicWiring.Resource(r.implType, r.effectHKTypeCtor, directImplToPureWiring(r.resourceImpl))
    }
  }

  private[this] def directImplToPureWiring(implementation: ImplDef.DirectImplDef): PureWiring = {
    implementation match {
      case i: ImplDef.TypeImpl =>
        reflectionProvider.symbolToWiring(i.implType)
      case p: ImplDef.ProviderImpl =>
        reflectionProvider.providerToWiring(p.function)
      case i: ImplDef.InstanceImpl =>
        SingletonWiring.Instance(i.implType, i.instance)
      case r: ImplDef.ReferenceImpl =>
        SingletonWiring.Reference(r.implType, r.key, r.weak)
    }
  }

  private[this] def setElementDiscriminatorKey(b: SetElementBinding[DIKey], currentPlan: DodgyPlan): DIKey = {
    val goodIdx = currentPlan.operations.size.toString

    val tpe = b.implementation match {
      case i: ImplDef.TypeImpl =>
        DIKey.TypeKey(i.implType)
      case r: ImplDef.ReferenceImpl =>
        r.key
      case i: ImplDef.InstanceImpl =>
        DIKey.TypeKey(i.implType).named(s"instance:$goodIdx")
      case p: ImplDef.ProviderImpl =>
        DIKey.TypeKey(p.implType).named(s"provider:$goodIdx")
      case e: ImplDef.EffectImpl =>
        DIKey.TypeKey(e.implType).named(s"effect:$goodIdx")
      case r: ImplDef.ResourceImpl =>
        DIKey.TypeKey(r.implType).named(s"resource:$goodIdx")
    }

    tpe
  }
}
