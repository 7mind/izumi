package izumi.distage.planning

import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan.initial.{NextOps, PrePlan}
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.planning._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, Provider, Wiring}
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ReflectionLock

trait BindingTranslator {
  def computeProvisioning(currentPlan: PrePlan, binding: Binding): NextOps
}

object BindingTranslator {

  class Impl(hook: PlanningHook) extends BindingTranslator {
    def computeProvisioning(currentPlan: PrePlan, binding: Binding): NextOps = {
      binding match {
        case singleton: SingletonBinding[_] =>
          NextOps(
            sets = Map.empty,
            provisions = provisionSingleton(singleton),
          )

        case set: SetElementBinding =>
          val target = set.key
          val elementKey = target
          val setKey = set.key.set

          val next = computeProvisioning(currentPlan, SingletonBinding(elementKey, set.implementation, set.tags, set.origin))
          val oldSet = next.sets.getOrElse(target, CreateSet(setKey, target.tpe, Set.empty, OperationOrigin.UserBinding(binding)))
          val newSet = oldSet.copy(members = oldSet.members + elementKey)

          NextOps(
            sets = next.sets.updated(target, newSet),
            provisions = next.provisions,
          )

        case set: EmptySetBinding[_] =>
          val newSet = CreateSet(set.key, set.key.tpe, Set.empty, OperationOrigin.UserBinding(binding))

          NextOps(
            sets = Map(set.key -> newSet),
            provisions = Seq.empty,
          )
      }
    }

    private[this] def provisionSingleton(binding: Binding.ImplBinding): Seq[InstantiationOp] = {
      val target = binding.key
      val wiring0 = implToWiring(binding.implementation)
      val wiring = hook.hookWiring(binding, wiring0)

      wiringToInstantiationOp(target, binding, wiring)
    }

    private[this] def wiringToInstantiationOp(target: DIKey, binding: Binding, wiring: Wiring): Seq[InstantiationOp] = {
      wiring match {
        case w: PureWiring =>
          Seq(pureWiringToWiringOp(target, binding, w))

        case w: MonadicWiring.Effect =>
          val effectKey = DIKey.EffectKey(target, w.effectWiring.instanceType)
          val effectOp = pureWiringToWiringOp(effectKey, binding, w.effectWiring)
          val execOp = MonadicOp.ExecuteEffect(target, effectKey, w.instanceType, w.effectHKTypeCtor, OperationOrigin.UserBinding(binding))
          Seq(effectOp, execOp)

        case w: MonadicWiring.Resource =>
          val resourceKey = DIKey.ResourceKey(target, w.effectWiring.instanceType)
          val resourceOp = pureWiringToWiringOp(resourceKey, binding, w.effectWiring)
          val allocOp = MonadicOp.AllocateResource(target, resourceKey, w.instanceType, w.effectHKTypeCtor, OperationOrigin.UserBinding(binding))
          Seq(resourceOp, allocOp)
      }
    }

    private[this] def pureWiringToWiringOp(target: DIKey, binding: Binding, wiring: PureWiring): WiringOp = {
      val userBinding = OperationOrigin.UserBinding(binding)
      wiring match {
        case w: Constructor =>
          // FIXME: non-existent wirings de-cake compile-time universe ???
          throw new Exception("WiringOp.InstantiateClass(target, w, Some(binding))")

        case w: AbstractSymbol =>
          throw new Exception("WiringOp.InstantiateTrait(target, w, Some(binding))")

        case w: Factory =>
          throw new Exception("WiringOp.InstantiateFactory(target, w, Some(binding))")

        case w: FactoryFunction =>
          WiringOp.CallFactoryProvider(target, w, userBinding)

        case w: Function =>
          WiringOp.CallProvider(target, w, userBinding)

        case w: Instance =>
          WiringOp.UseInstance(target, w, userBinding)

        case w: Reference =>
          WiringOp.ReferenceKey(target, w, userBinding)
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
        case p: ImplDef.ProviderImpl =>
          providerToWiring(p.function)

        case i: ImplDef.InstanceImpl =>
          SingletonWiring.Instance(i.implType, i.instance)

        case r: ImplDef.ReferenceImpl =>
          SingletonWiring.Reference(r.implType, r.key, r.weak)
      }
    }

    private[this] def providerToWiring(function: Provider): Wiring.PureWiring = ReflectionLock.synchronized {
      function match {
        case factory: Provider.FactoryProvider =>
          Wiring.FactoryFunction(factory, factory.factoryIndex, factory.associations)
        case _ =>
          Wiring.SingletonWiring.Function(function, function.associations)
      }
    }

  }
}
