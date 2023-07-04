package izumi.distage.planning

import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.errors.LocalContextFailure
import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan.Wiring
import izumi.distage.model.plan.Wiring.*
import izumi.distage.model.plan.Wiring.SingletonWiring.*
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.BindingTranslator.NextOps

trait BindingTranslator {
  def computeProvisioning(handler: LocalContextHandler, binding: Binding): Either[LocalContextFailure, NextOps]
}

object BindingTranslator {

  final case class NextOps(
    sets: Map[DIKey, CreateSet],
    provisions: Seq[InstantiationOp],
  )

  class Impl extends BindingTranslator {
    def computeProvisioning(handler: LocalContextHandler, binding: Binding): Either[LocalContextFailure, NextOps] = {
      binding match {
        case singleton: SingletonBinding[?] =>
          for {
            provisions <- provisionSingleton(handler, singleton)
          } yield {
            NextOps(
              sets = Map.empty,
              provisions = provisions,
            )
          }

        case set: SetElementBinding =>
          val target = set.key
          val elementKey = target
          val setKey = set.key.set

          for {
            next <- computeProvisioning(handler, SingletonBinding(elementKey, set.implementation, set.tags, set.origin))
          } yield {
            val oldSet = next.sets.getOrElse(target, CreateSet(setKey, Set.empty, OperationOrigin.UserBinding(binding)))
            val newSet = oldSet.copy(members = oldSet.members + elementKey)

            NextOps(
              sets = next.sets.updated(target, newSet),
              provisions = next.provisions,
            )
          }

        case set: EmptySetBinding[?] =>
          val newSet = CreateSet(set.key, Set.empty, OperationOrigin.UserBinding(binding))

          Right(
            NextOps(
              sets = Map(set.key -> newSet),
              provisions = Seq.empty,
            )
          )
      }
    }

    private[this] def provisionSingleton(handler: LocalContextHandler, binding: Binding.ImplBinding): Either[LocalContextFailure, Seq[InstantiationOp]] = {
      val target = binding.key
      for {
        wiring <- implToWiring(handler, binding.implementation)
      } yield {
        wiringToInstantiationOp(target, binding, wiring)
      }
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

        case w: PrepareLocalContext =>
          WiringOp.LocalContext(target, w, userBinding)
      }
    }

    private[this] def implToWiring(handler: LocalContextHandler, implementation: ImplDef): Either[LocalContextFailure, Wiring] = {
      implementation match {
        case d: ImplDef.DirectImplDef =>
          directImplToPureWiring(handler, d)

        case e: ImplDef.EffectImpl =>
          for {
            w <- directImplToPureWiring(handler, e.effectImpl)
          } yield {
            MonadicWiring.Effect(e.implType, e.effectHKTypeCtor, w)
          }

        case r: ImplDef.ResourceImpl =>
          for {
            w <- directImplToPureWiring(handler, r.resourceImpl)
          } yield {
            MonadicWiring.Resource(r.implType, r.effectHKTypeCtor, w)
          }
      }
    }

    private[this] def directImplToPureWiring(handler: LocalContextHandler, implementation: ImplDef.DirectImplDef): Either[LocalContextFailure, SingletonWiring] = {
      implementation match {
        case p: ImplDef.ProviderImpl =>
          Right(Wiring.SingletonWiring.Function(p.function))

        case i: ImplDef.InstanceImpl =>
          Right(SingletonWiring.Instance(i.implType, i.instance))

        case r: ImplDef.ReferenceImpl =>
          Right(SingletonWiring.Reference(r.implType, r.key, r.weak))

        case c: ImplDef.ContextImpl =>
          handler.handle(c)
      }
    }

  }
}
