package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.Planner
import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleDef, ImplDef}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CustomOp, ImportDependency, SetOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.functional.Value


class PlannerDefaultImpl
(
  protected val planResolver: PlanResolver
  , protected val forwardingRefResolver: ForwardingRefResolver
  , protected val reflectionProvider: ReflectionProvider.Runtime
  , protected val sanityChecker: SanityChecker
  , protected val customOpHandler: CustomOpHandler
  , protected val planningObserver: PlanningObserver
  , protected val planMergingPolicy: PlanMergingPolicy
  , protected val planningHooks: Set[PlanningHook]
)
  extends Planner {
  private val hook = new PlanningHookAggregate(planningHooks)

  override def plan(context: ModuleDef): FinalPlan = {
    val plan = context.bindings.foldLeft(DodgyPlan.empty) {
      case (currentPlan, binding) =>
        Value(computeProvisioning(currentPlan, binding))
          .eff(sanityChecker.assertNoDuplicateOps)
          .map(planMergingPolicy.extendPlan(currentPlan, binding, _))
          .eff(sanityChecker.assertNoDuplicateOps)
          .map(hook.hookStep(context, currentPlan, binding, _))
          .eff(planningObserver.onSuccessfulStep)
          .get
    }

    val finalPlan = Value(plan)
      .map(planMergingPolicy.resolve)
      .map(forwardingRefResolver.resolve)
      .eff(planningObserver.onReferencesResolved)
      .map(planResolver.resolve(_, context))
      .eff(planningObserver.onResolvingFinished)
      .eff(sanityChecker.assertSanity)
      .map(hook.hookFinal)
      .eff(sanityChecker.assertSanity)
      .eff(planningObserver.onFinalPlan)
      .get

    finalPlan
  }

  private def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): NextOps = {
    binding match {
      case c: SingletonBinding[_] =>
        val newOps = provisioning(c)

        val imports = computeImports(currentPlan, binding, newOps.wiring)
        NextOps(
          imports
          , Map.empty
          , Seq(newOps.op)
        )

      case s: SetElementBinding[_] =>
        val target = s.key
        val elementKey = RuntimeDIUniverse.DIKey.SetElementKey(target, setElementKeySymbol(s.implementation))
        val next = computeProvisioning(currentPlan, SingletonBinding(elementKey, s.implementation))
        val oldSet = next.sets.getOrElse(target, SetOp.CreateSet(s.key, s.key.symbol, Set.empty))
        val newSet = oldSet.copy(members = oldSet.members + elementKey)

        NextOps(
          next.imports
          , next.sets.updated(target, newSet)
          , next.provisions
        )

      case s: EmptySetBinding[_] =>
        val newSet = SetOp.CreateSet(s.key, s.key.symbol, Set.empty)

        NextOps(
          Set.empty
          , Map(s.key -> newSet)
          , Seq.empty
        )
    }
  }

  private def provisioning(binding: Binding.ImplBinding): Step = {
    val target = binding.key
    val wiring = hook.hookWiring(binding, bindingToWireable(binding))

    wiring match {
      case w: Constructor =>
        Step(wiring, WiringOp.InstantiateClass(target, w))

      case w: AbstractSymbol =>
        Step(wiring, WiringOp.InstantiateTrait(target, w))

      case w: FactoryMethod =>
        Step(wiring, WiringOp.InstantiateFactory(target, w))

      case w: Function =>
        Step(wiring, WiringOp.CallProvider(target, w))

      case w: Instance =>
        Step(wiring, WiringOp.ReferenceInstance(target, w))

      case w: CustomWiring =>
        Step(wiring, CustomOp(target, w))
    }
  }

  private def computeImports(currentPlan: DodgyPlan, binding: Binding, deps: RuntimeDIUniverse.Wiring): Set[ImportDependency] = {
    val knownTargets = currentPlan.steps.map(_.target).toSet
    // we don't need resolved deps, we already have them in finalPlan
    val (_, unresolved) = deps.associations.partition(dep => knownTargets.contains(dep.wireWith) && !currentPlan.imports.contains(dep.wireWith))
    val toImport = unresolved.map(dep => ImportDependency(dep.wireWith, Set(binding.key)))
    toImport.toSet
  }

  private def bindingToWireable(binding: Binding.ImplBinding): RuntimeDIUniverse.Wiring = {
    binding.implementation match {
      case i: ImplDef.TypeImpl =>
        reflectionProvider.symbolToWiring(i.implType)
      case i: ImplDef.InstanceImpl =>
        UnaryWiring.Instance(i.implType, i.instance)
      case p: ImplDef.ProviderImpl =>
        reflectionProvider.providerToWiring(p.function)
      case c: ImplDef.CustomImpl =>
        customOpHandler.getDeps(c)
    }
  }

  private def setElementKeySymbol(impl: ImplDef): RuntimeDIUniverse.TypeFull = {
    impl match {
      case i: ImplDef.TypeImpl =>
        i.implType
      case i: ImplDef.InstanceImpl =>
        i.implType
      case p: ImplDef.ProviderImpl =>
        p.implType
      case c: ImplDef.CustomImpl =>
        customOpHandler.getSymbol(c)
    }
  }
}
