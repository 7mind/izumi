package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.Planner
import com.github.pshirshov.izumi.distage.model.definition.BindingT.{EmptySetBinding, SetBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ContextDefinition, ImplDef}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CustomOp, ImportDependency, SetOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse.Wiring.UnaryWiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse.Wiring._
import com.github.pshirshov.izumi.functional.Value


class PlannerDefaultImpl
(
  protected val planResolver: PlanResolver
  , protected val forwardingRefResolver: ForwardingRefResolver
  , protected val reflectionProvider: ReflectionProvider.Java
  , protected val sanityChecker: SanityChecker
  , protected val customOpHandler: CustomOpHandler
  , protected val planningObserver: PlanningObserver
  , protected val planMergingPolicy: PlanMergingPolicy
  , protected val planningHook: PlanningHook
)
  extends Planner {

  override def plan(context: ContextDefinition): FinalPlan = {
    val plan = context.bindings.foldLeft(DodgyPlan.empty) {
      case (currentPlan, binding) =>
        Value(computeProvisioning(currentPlan, binding))
          .eff(sanityChecker.assertNoDuplicateOps)
          .map(planMergingPolicy.extendPlan(currentPlan, binding, _))
          .eff(sanityChecker.assertNoDuplicateOps)
          .map(planningHook.hookStep(context, currentPlan, binding, _))
          .eff(planningObserver.onSuccessfulStep)
          .get
    }

    val finalPlan = Value(plan)
      .map(forwardingRefResolver.resolve)
      .eff(planningObserver.onReferencesResolved)
      .map(planResolver.resolve(_, context))
      .eff(planningObserver.onResolvingFinished)
      .eff(sanityChecker.assertSanity)
      .eff(planningObserver.onFinalPlan)
      .get

    finalPlan
  }


  private def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): NextOps = {
    binding match {
      case c: SingletonBinding =>
        val newOps = provisioning(c)

        val imports = computeImports(currentPlan, binding, newOps.wiring)
        NextOps(
          imports
          , Set.empty
          , newOps.ops
        )

      case s: SetBinding =>
        val target = s.target
        val elementKey = RuntimeUniverse.DIKey.SetElementKey(target, setElementKeySymbol(s.implementation))

        val next = computeProvisioning(currentPlan, SingletonBinding(elementKey, s.implementation))
        NextOps(
          next.imports
          , next.sets + SetOp.CreateSet(target, target.symbol)
          , next.provisions :+ SetOp.AddToSet(target, elementKey)
        )

      case s: EmptySetBinding =>
        NextOps(
          Set.empty
          , Set(SetOp.CreateSet(s.target, s.target.symbol))
          , Seq.empty
        )
    }
  }

  private def provisioning(binding: SingletonBinding): Step = {
    val target = binding.target
    val wiring = implToWireable(binding.implementation)
    wiring match {
      case w: Constructor =>
        Step(wiring, Seq(WiringOp.InstantiateClass(target, w)))

      case w: Abstract =>
        Step(wiring, Seq(WiringOp.InstantiateTrait(target, w)))

      case w: FactoryMethod =>
        Step(wiring, Seq(WiringOp.InstantiateFactory(target, w)))

      case w: Function =>
        Step(wiring, Seq(WiringOp.CallProvider(target, w)))

      case w: Instance =>
        Step(wiring, Seq(WiringOp.ReferenceInstance(target, w)))

      case w: CustomWiring =>
        Step(wiring, Seq(CustomOp(target, w)))
    }

  }

  private def computeImports(currentPlan: DodgyPlan, binding: Binding, deps: RuntimeUniverse.Wiring): Set[ImportDependency] = {
    val knownTargets = currentPlan.statements.map(_.target).toSet
    val (_, unresolved) = deps.associations.partition(dep => knownTargets.contains(dep.wireWith))
    // we don't need resolved deps, we already have them in finalPlan
    val toImport = unresolved.map(dep => ImportDependency(dep.wireWith, Set(binding.target)))
    toImport.toSet
  }

  private def implToWireable(impl: ImplDef): RuntimeUniverse.Wiring = {
    impl match {
      case i: ImplDef.TypeImpl =>
        reflectionProvider.symbolToWiring(i.implType)
      case p: ImplDef.ProviderImpl =>
        reflectionProvider.providerToWiring(p.function)
      case c: ImplDef.CustomImpl =>
        customOpHandler.getDeps(c)
      case i: ImplDef.InstanceImpl =>
        UnaryWiring.Instance(i.implType, i.instance)
    }
  }

  private def setElementKeySymbol(impl: ImplDef): RuntimeUniverse.TypeFull = {
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
