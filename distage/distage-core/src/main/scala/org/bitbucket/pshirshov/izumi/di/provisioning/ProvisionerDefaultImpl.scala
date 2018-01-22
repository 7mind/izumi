package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.Locator
import org.bitbucket.pshirshov.izumi.di.model.EqualitySafeType
import org.bitbucket.pshirshov.izumi.di.model.exceptions._
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, FinalPlan}

import scala.collection.mutable
import scala.reflect.runtime._


class ProvisionerDefaultImpl(
                              provisionerHook: ProvisionerHook
                              , provisionerIntrospector: ProvisionerIntrospector
                            ) extends Provisioner {
  override def provision(plan: FinalPlan, parentContext: Locator): ProvisionImmutable = {
    val activeProvision = ProvisionActive()

    val provisions = plan.steps.foldLeft(activeProvision) {
      case (active, step) =>
        val immutable = active.toImmutable
        val result = execute(LocatorContext(active, parentContext), step)

        result match {
          case OpResult.NewImport(target, value) =>
            if (active.imports.contains(target)) {
              throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
            }
            plan.synchronized {
              active.imports += (target -> value)
            }
            active

          case OpResult.NewInstance(target, value) =>
            if (active.instances.contains(target)) {
              throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
            }
            plan.synchronized {
              active.instances += (target -> value)
            }
            active

          case OpResult.SetElement(set, instance) =>
            plan.synchronized {
              set += instance
            }
            active
        }
    }

    ProvisionImmutable(provisions.instances, provisions.imports)
  }

  private def execute(context: ProvisioningContext, step: ExecutableOp): OpResult = {
    step match {
      case ExecutableOp.ImportDependency(target, references) =>
        context.importKey(target) match {
          case Some(v) =>
            OpResult.NewImport(target, v)
          case _ =>
            throw new MissingInstanceException(s"Instance is not available in the context: $target. " +
              s"required by refs: $references", target)
        }

      case ExecutableOp.SetOp.CreateSet(target, targetType) =>
        // target is guaranteed to be a Set
        val scalaCollectionSetType = EqualitySafeType.get[scala.collection.Set[_]]
        if (targetType.tpe.baseClasses.contains(scalaCollectionSetType.tpe)) {
          OpResult.NewInstance(target, mutable.HashSet[Any]())
        } else {
          throw new IncompatibleTypesException("Tried to create make a Set with a non-Set type! " +
            s"For $target expected $targetType to be a sub-class of $scalaCollectionSetType, but it isn't!"
            , scalaCollectionSetType
            , targetType)
        }

      case ExecutableOp.SetOp.AddToSet(target, key) =>
        // value is guaranteed to have already been instantiated or imported
        context.fetchKey(key) match {
          case Some(value) =>
            // set is guaranteed to have already been added
            context.fetchKey(target) match {
              case Some(set: mutable.HashSet[_]) =>
                OpResult.SetElement(set.asInstanceOf[mutable.HashSet[Any]], value)

              case Some(somethingElse) =>
                throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
                  s" but target Set is not a Set! It's ${somethingElse.getClass.getName}")
              case _ =>
                throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
                  s" but Set has not been initialized! Set: $target, instance: $key")
            }
          case _ =>
            throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
              s" but the instance has not been initialized! Set: $target, instance: $key")
        }

      case ExecutableOp.WiringOp.ReferenceInstance(target, wiring) =>
        OpResult.NewInstance(target, wiring.instance)

      case ExecutableOp.CustomOp(target, customDef) =>
        throw new DIException(s"No handle for CustomOp for $target, defs: $customDef", null)

      case ExecutableOp.WiringOp.CallProvider(target, associations) =>
        // TODO: here we depend on order
        val args = associations.associations.map {
          key =>
            context.fetchKey(key.wireWith) match {
              case Some(dep) =>
                dep
              case _ =>
                throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
                  s" but the dependency has not been initialized: Class: $target, dependency: $key")
            }
        }

        val instance = associations.provider.apply(args: _*)
        OpResult.NewInstance(target, instance)

      case ExecutableOp.WiringOp.InstantiateClass(target, wiring) =>
        val depMap = wiring.associations.map {
          key =>
            context.fetchKey(key.wireWith) match {
              case Some(dep) =>
                key.symbol -> dep
              case _ =>
                throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
                  s" but the dependency has not been initialized: Class: $target, dependency: $key")
            }
        }.toMap

        val targetType = wiring.instanceType
        val refUniverse = currentMirror
        val refClass = refUniverse.reflectClass(targetType.tpe.typeSymbol.asClass)
        val ctor = targetType.tpe.decl(universe.termNames.CONSTRUCTOR).asMethod
        val refCtor = refClass.reflectConstructor(ctor)

        val orderedArgs = ctor.paramLists.head.map {
          key => depMap(key)
        }

        val instance = refCtor.apply(orderedArgs: _*)
        OpResult.NewInstance(target, instance)

      case t: ExecutableOp.WiringOp.InstantiateTrait =>
        ???

      case f: ExecutableOp.WiringOp.InstantiateFactory =>
        val allRequiredKeys = f.wiring.associations.map(_.wireWith)
        val executor = mkExecutor(context)
        ???


      case m: ExecutableOp.ProxyOp.MakeProxy =>
        ???

      case i: ExecutableOp.ProxyOp.InitProxies =>
        // at this point we definitely have all the dependencies instantiated
        val allRequiredKeys = i.op.wiring.associations
        val executor = mkExecutor(context)
        ???

    }
  }

  private def mkExecutor(context: ProvisioningContext) = {
    new OperationExecutor {
      override def execute(step: ExecutableOp): OpResult = {
        ProvisionerDefaultImpl.this.execute(context, step)
      }
    }
  }
}
