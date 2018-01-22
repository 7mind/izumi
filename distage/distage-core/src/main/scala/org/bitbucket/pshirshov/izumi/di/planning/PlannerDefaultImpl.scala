package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ContextDefinition, ImplDef}
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.ImportDependency
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningFailure.UnbindableBinding
import org.bitbucket.pshirshov.izumi.di.model.plan.Provisioning.{Impossible, InstanceProvisioning, Possible, StepProvisioning}
import org.bitbucket.pshirshov.izumi.di.model.plan.Wiring._
import org.bitbucket.pshirshov.izumi.di.model.plan._
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, Value}
import org.bitbucket.pshirshov.izumi.di.reflection.ReflectionProvider
import org.bitbucket.pshirshov.izumi.di.{Planner, TypeFull}


class PlannerDefaultImpl
(
  protected val planResolver: PlanResolver
  , protected val forwardingRefResolver: ForwardingRefResolver
  , protected val reflectionProvider: ReflectionProvider
  , protected val sanityChecker: SanityChecker
  , protected val customOpHandler: CustomOpHandler
  , protected val planningObserver: PlanningObsever
  , protected val planMergingPolicy: PlanMergingPolicy
)
  extends Planner {

  override def plan(context: ContextDefinition): FinalPlan = {
    val plan = context.bindings.foldLeft(DodgyPlan.empty) {
      case (currentPlan, binding) =>
        val nextOps = computeProvisioning(currentPlan, binding)

        nextOps match {
          case Possible(ops) =>
            sanityChecker.assertNoDuplicateOps(ops.flatten)
            val next = planMergingPolicy.extendPlan(currentPlan, binding, ops)
            sanityChecker.assertNoDuplicateOps(next.statements)
            planningObserver.onSuccessfulStep(next)
            next

          case Impossible(implDefs) =>
            val next = currentPlan.copy(issues = currentPlan.issues :+ UnbindableBinding(binding, implDefs))
            planningObserver.onFailedStep(next)
            next
        }
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


  private def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): StepProvisioning = {
    binding match {
      case c: SingletonBinding =>
        val toProvision = provisioning(c)

        toProvision
          .map {
            newOps =>
              val imports = computeImports(currentPlan, binding, newOps.wiring)

              NextOps(
                imports
                , Set.empty
                , newOps.ops
              )
          }

      case s: SetBinding =>
        val target = s.target
        val elementKey = DIKey.SetElementKey(target, setElementKeySymbol(s.implementation))

        computeProvisioning(currentPlan, SingletonBinding(elementKey, s.implementation))
          .map { next =>
            NextOps(
              next.imports
              , next.sets + ExecutableOp.SetOp.CreateSet(target, target.symbol)
              , next.provisions :+ ExecutableOp.SetOp.AddToSet(target, elementKey)
            )
          }

      case s: EmptySetBinding =>
        Possible(NextOps(
          Set.empty
          , Set(ExecutableOp.SetOp.CreateSet(s.target, s.target.symbol))
          , Seq.empty
        ))
    }
  }

  private def provisioning(binding: SingletonBinding): InstanceProvisioning = {
    val target = binding.target
    val wiring = implToWireable(binding.implementation)

    wiring match {
      case w: Constructor =>
        Possible(Step(wiring, Seq(ExecutableOp.WiringOp.InstantiateClass(target, w))))

      case w: Abstract =>
        Possible(Step(wiring, Seq(ExecutableOp.WiringOp.InstantiateTrait(target, w))))

      case w: FactoryMethod =>
        Possible(Step(wiring, Seq(ExecutableOp.WiringOp.InstantiateFactory(target, w))))

      case w: Function =>
        Possible(Step(wiring, Seq(ExecutableOp.WiringOp.CallProvider(target, w))))

      case w: Instance =>
        Possible(Step(wiring, Seq(ExecutableOp.WiringOp.ReferenceInstance(target, w))))

      case w: CustomWiring =>
        Possible(Step(wiring, Seq(ExecutableOp.CustomOp(target, w))))
    }

  }

  private def computeImports(currentPlan: DodgyPlan, binding: Binding, deps: Wiring): Set[ImportDependency] = {
    val knownTargets = currentPlan.statements.map(_.target).toSet
    val (_, unresolved) = deps.associations.partition(dep => knownTargets.contains(dep.wireWith))
    // we don't need resolved deps, we already have them in finalPlan
    val toImport = unresolved.map(dep => ExecutableOp.ImportDependency(dep.wireWith, Set(binding.target)))
    toImport.toSet
  }

  private def implToWireable(impl: ImplDef): Wiring = {
    impl match {
      case i: ImplDef.TypeImpl =>
        reflectionProvider.symbolDeps(i.implType)
      case p: ImplDef.ProviderImpl =>
        reflectionProvider.providerDeps(p.function)
      case c: ImplDef.CustomImpl =>
        customOpHandler.getDeps(c)
      case i: ImplDef.InstanceImpl =>
        Wiring.Instance(i.implType, i.instance)
    }
  }

  private def setElementKeySymbol(impl: ImplDef): TypeFull = {
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
