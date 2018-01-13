package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ContextDefinition, ImplDef}
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningFailure.UnbindableBinding
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.ImportDependency
import org.bitbucket.pshirshov.izumi.di.model.plan.Provisioning.{Impossible, InstanceProvisioning, Possible, StepProvisioning}
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
  , protected val planningObsever: PlanningObsever
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
            planningObsever.onSuccessfulStep(next)
            next

          case Impossible(implDefs) =>
            val next = currentPlan.copy(issues = currentPlan.issues :+ UnbindableBinding(binding, implDefs))
            planningObsever.onFailedStep(next)
            next
        }
    }

    val finalPlan = Value(plan)
      .map(forwardingRefResolver.resolve)
      .eff(planningObsever.onReferencesResolved)
      .map(planResolver.resolve(_, context))
      .eff(planningObsever.onResolvingFinished)
      .eff(sanityChecker.assertSanity)
      .eff(planningObsever.onFinalPlan)
      .get

    finalPlan
  }

  private def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): StepProvisioning = {
    binding match {
      case c: SingletonBinding =>
        val deps = enumerateDeps(c)
        val toImport = computeImports(currentPlan, binding, deps)
        val toProvision = provisioning(c, deps)
        toProvision
          .map(newOps => NextOps(
            toImport
            , Set.empty
            , newOps
          ))

      case s: SetBinding =>
        val target = s.target
        val elementKey = DIKey.SetElementKey(target, getSymbol(s.implementation))

        computeProvisioning(currentPlan, SingletonBinding(elementKey, s.implementation))
          .map { next =>
            NextOps(
              next.imports
              , next.sets + ExecutableOp.CreateSet(target, target.symbol)
              , next.provisions :+ ExecutableOp.AddToSet(target, elementKey)
            )
          }

      case s: EmptySetBinding =>
        Possible(NextOps(
          Set.empty
          , Set(ExecutableOp.CreateSet(s.target, s.target.symbol))
          , Seq.empty
        ))
    }
  }

  private def computeImports(currentPlan: DodgyPlan, binding: Binding, deps: Seq[Association]): Set[ImportDependency] = {
    val knownTargets = currentPlan.statements.map(_.target).toSet
    val (resolved@_, unresolved) = deps.partition(d => knownTargets.contains(d.wireWith))
    // we don't need resolved deps, we already have them in finalPlan
    val toImport = unresolved.map(dep => ExecutableOp.ImportDependency(dep.wireWith, Set(binding.target)))
    toImport.toSet
  }

  private def provisioning(binding: SingletonBinding, deps: Seq[Association]): InstanceProvisioning = {
    import Provisioning._
    val target = binding.target

    binding.implementation match {
      case ImplDef.TypeImpl(symb) if reflectionProvider.isConcrete(symb) =>
        Possible(Seq(ExecutableOp.InstantiateClass(target, symb, deps)))

      case ImplDef.TypeImpl(symb) if reflectionProvider.isWireableAbstract(symb) =>
        Possible(Seq(ExecutableOp.InstantiateTrait(target, symb, deps)))

      case ImplDef.TypeImpl(symb) if reflectionProvider.isFactory(symb) =>
        Possible(Seq(ExecutableOp.InstantiateFactory(target, symb, deps)))

      case ImplDef.InstanceImpl(symb, instance) =>
        Possible(Seq(ExecutableOp.ReferenceInstance(target, symb, instance)))

      case ImplDef.CustomImpl(instance) =>
        Possible(Seq(ExecutableOp.CustomOp(target, instance)))

      case other =>
        Impossible(Seq(other))
    }
  }


  private def enumerateDeps(definition: Binding): Seq[Association] = {
    definition match {
      case c: SingletonBinding =>
        enumerateDeps(c.implementation)
      case c: SetBinding =>
        enumerateDeps(c.implementation)
      case _ =>
        Seq()
    }
  }

  private def enumerateDeps(impl: ImplDef): Seq[Association] = {
    impl match {
      case i: ImplDef.TypeImpl =>
        reflectionProvider.symbolDeps(i.impl)
      case _: ImplDef.InstanceImpl =>
        Seq()
      case c: ImplDef.CustomImpl =>
        customOpHandler.getDeps(c)
    }
  }

  private def getSymbol(impl: ImplDef): TypeFull = {
    impl match {
      case i: ImplDef.TypeImpl =>
        i.impl
      case i: ImplDef.InstanceImpl =>
        i.tpe
      case c: ImplDef.CustomImpl =>
        customOpHandler.getSymbol(c)
    }
  }
}
