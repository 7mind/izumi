package izumi.distage.planning

import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan._
import izumi.distage.model.planning._
import izumi.distage.model.reflection.ReflectionProvider
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, Wiring}

trait BindingTranslator {
  def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): NextOps
}

class BindingTranslatorImpl(
                             protected val reflectionProvider: ReflectionProvider.Runtime,
                             protected val hook: PlanningHook,

                           ) extends BindingTranslator {
  def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): NextOps = {
    binding match {
      case singleton: SingletonBinding[_] =>
        val newOp = provisionSingleton(singleton)

        NextOps(
          sets = Map.empty
          , provisions = Seq(newOp)
        )

      case set: SetElementBinding =>
        val target = set.key
        //val discriminator = setElementDiscriminatorKey(set, currentPlan)
        //val elementKey = DIKey.SetElementKey(target, discriminator)
        val elementKey = target
        val setKey = set.key.set

        val next = computeProvisioning(currentPlan, SingletonBinding(elementKey, set.implementation, set.tags, set.origin))
        println(("!!!", setKey))
        val oldSet = next.sets.getOrElse(target, CreateSet(setKey, target.tpe, Set.empty, Some(binding)))
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

//  private[this] def setElementDiscriminatorKey(b: SetElementBinding, currentPlan: DodgyPlan): DIKey = {
//    val goodIdx = currentPlan.size.toString
//
//    val tpe = b.implementation match {
//      case i: ImplDef.TypeImpl =>
//        DIKey.TypeKey(i.implType)
//
//      case r: ImplDef.ReferenceImpl =>
//        r.key
//
//      case i: ImplDef.InstanceImpl =>
//        DIKey.TypeKey(i.implType).named(s"instance:$goodIdx")
//
//      case p: ImplDef.ProviderImpl =>
//        DIKey.TypeKey(p.implType).named(s"provider:$goodIdx")
//
//      case e: ImplDef.EffectImpl =>
//        DIKey.TypeKey(e.implType).named(s"effect:$goodIdx")
//
//      case r: ImplDef.ResourceImpl =>
//        DIKey.TypeKey(r.implType).named(s"resource:$goodIdx")
//    }
//
//    tpe
//  }

}
