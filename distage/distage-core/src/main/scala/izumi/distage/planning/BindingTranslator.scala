package izumi.distage.planning

import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.{Binding, ImplDef}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan.Wiring
import izumi.distage.model.plan.Wiring.*
import izumi.distage.model.plan.Wiring.SingletonWiring.*
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.BindingTranslator.NextOps

trait BindingTranslator {
  def computeProvisioning[Err](handler: SubcontextHandler[Err], binding: Binding): Either[Err, NextOps]
}

object BindingTranslator {

  final case class NextOps(
    sets: Map[DIKey, CreateSet],
    provisions: Seq[InstantiationOp],
  )

  class Impl extends BindingTranslator {
    def computeProvisioning[Err](handler: SubcontextHandler[Err], binding: Binding): Either[Err, NextOps] = {
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

    private def provisionSingleton[Err](handler: SubcontextHandler[Err], binding: Binding.ImplBinding): Either[Err, Seq[InstantiationOp]] = {
      val target = binding.key
      for {
        wiring <- implToWiring(handler, binding)
      } yield {
        wiringToInstantiationOp(target, binding, wiring)
      }
    }

    private def wiringToInstantiationOp(target: DIKey, binding: Binding, wiring: Wiring): Seq[InstantiationOp] = {
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

    private def pureWiringToWiringOp(target: DIKey, binding: Binding, wiring: SingletonWiring): WiringOp = {
      val userBinding = OperationOrigin.UserBinding(binding)
      wiring match {
        case w: Function =>
          WiringOp.CallProvider(target, w, userBinding)

        case w: Instance =>
          WiringOp.UseInstance(target, w, userBinding)

        case w: Reference =>
          WiringOp.ReferenceKey(target, w, userBinding)

        case w: PrepareSubcontext =>
          // it's safe to import self reference and it can always be synthesised within a context
          val removedSelfImport = w.importedFromParentKeys.diff(Set(target))
          WiringOp.CreateSubcontext(target, w.copy(importedFromParentKeys = removedSelfImport), userBinding)
      }
    }

    private def implToWiring[Err](handler: SubcontextHandler[Err], binding: Binding.ImplBinding): Either[Err, Wiring] = {
      binding.implementation match {
        case d: ImplDef.DirectImplDef =>
          directImplToPureWiring(handler, binding, d)

        case e: ImplDef.EffectImpl =>
          for {
            w <- directImplToPureWiring(handler, binding, e.effectImpl)
          } yield {
            MonadicWiring.Effect(e.implType, e.effectHKTypeCtor, w)
          }

        case r: ImplDef.ResourceImpl =>
          for {
            w <- directImplToPureWiring(handler, binding, r.resourceImpl)
          } yield {
            MonadicWiring.Resource(r.implType, r.effectHKTypeCtor, w)
          }
      }
    }

    private def directImplToPureWiring[Err](
      handler: SubcontextHandler[Err],
      binding: Binding,
      implementation: ImplDef.DirectImplDef,
    ): Either[Err, SingletonWiring] = {
      implementation match {
        case p: ImplDef.ProviderImpl =>
          Right(Wiring.SingletonWiring.Function(p.function))

        case i: ImplDef.InstanceImpl =>
          Right(SingletonWiring.Instance(i.implType, i.instance))

        case r: ImplDef.ReferenceImpl =>
          Right(SingletonWiring.Reference(r.implType, r.key, r.weak))

        case c: ImplDef.ContextImpl =>
          handler.handle(binding, c)
      }
    }

  }
}
