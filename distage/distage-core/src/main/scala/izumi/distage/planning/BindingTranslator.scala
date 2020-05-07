package izumi.distage.planning

import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan.Wiring
import izumi.distage.model.plan.Wiring.SingletonWiring._
import izumi.distage.model.plan.Wiring._
import izumi.distage.model.plan.initial.NextOps
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.reflection.DIKey

trait BindingTranslator {
  def computeProvisioning(binding: Binding): NextOps
}

object BindingTranslator {

  class Impl() extends BindingTranslator {
    def computeProvisioning(binding: Binding): NextOps = {
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

          val next = computeProvisioning(SingletonBinding(elementKey, set.implementation, set.tags, set.origin))
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
      val wiring = implToWiring(binding.implementation)
      wiringToInstantiationOp(target, binding, wiring)
    }

    private[this] def wiringToInstantiationOp(target: DIKey, binding: Binding, wiring: Wiring): Seq[InstantiationOp] = {
      wiring match {
        case w: SingletonWiring =>
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

    private[this] def pureWiringToWiringOp(target: DIKey, binding: Binding, wiring: SingletonWiring): WiringOp = {
      val userBinding = OperationOrigin.UserBinding(binding)
      wiring match {
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

    private[this] def directImplToPureWiring(implementation: ImplDef.DirectImplDef): SingletonWiring = {
      implementation match {
        case p: ImplDef.ProviderImpl =>
          Wiring.SingletonWiring.Function(p.function)

        case i: ImplDef.InstanceImpl =>
          SingletonWiring.Instance(i.implType, i.instance)

        case r: ImplDef.ReferenceImpl =>
          SingletonWiring.Reference(r.implType, r.key, r.weak)
      }
    }

  }
}
