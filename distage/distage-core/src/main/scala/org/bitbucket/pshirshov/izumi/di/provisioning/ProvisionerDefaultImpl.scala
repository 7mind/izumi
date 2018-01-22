package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.Locator
import org.bitbucket.pshirshov.izumi.di.model.EqualitySafeType
import org.bitbucket.pshirshov.izumi.di.model.exceptions._
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, FinalPlan}

import scala.collection.mutable
import scala.reflect.runtime._



class ProvisionerDefaultImpl extends Provisioner {
  override def provision(dIPlan: FinalPlan, parentContext: Locator): ProvisionedContext = {
    val provisions = dIPlan.steps.foldLeft(ActiveProvision()) {
      case (active, step) =>
        val result = stepToUpdate(parentContext, active, step)
        result match {
          case StepResult.NewImport(target, value) =>
            if (active.imports.contains(target)) {
              throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
            }
            active.imports += (target -> value)
            active

          case StepResult.NewInstance(target, value) =>
            if (active.instances.contains(target)) {
              throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
            }
            active.instances += (target -> value)
            active

          case StepResult.SetElement(set, instance) =>
            set += instance
            active
        }
    }

    ProvisionedContext(provisions.instances, provisions.imports)
  }

  private def stepToUpdate(parentContext: Locator, map: ActiveProvision, step: ExecutableOp): StepResult = {
    step match {
      case ExecutableOp.ImportDependency(target, references) =>
        parentContext.lookupInstance[Any](target) match {
          case Some(v) =>
            StepResult.NewImport(target, v)
          case _ =>
            throw new MissingInstanceException(s"Instance is not available in the context: $target. " +
              s"required by refs: $references", target)
        }

      case ExecutableOp.SetOp.CreateSet(target, targetType) =>
        // target is guaranteed to be a Set
        val scalaCollectionSetType = EqualitySafeType.get[scala.collection.Set[_]]
        if (targetType.tpe.baseClasses.contains(scalaCollectionSetType.tpe)) {
          StepResult.NewInstance(target, mutable.HashSet[Any]())
        } else {
          throw new IncompatibleTypesException("Tried to create make a Set with a non-Set type! " +
            s"For $target expected $targetType to be a sub-class of $scalaCollectionSetType, but it isn't!"
            , scalaCollectionSetType
            , targetType)
        }

      case ExecutableOp.SetOp.AddToSet(target, key) =>
        // value is guaranteed to have already been instantiated or imported
        map.get(key) match {
          case Some(value) =>
            // set is guaranteed to have already been added
            map.get(target) match {
              case Some(set: mutable.HashSet[_]) =>
                StepResult.SetElement(set.asInstanceOf[mutable.HashSet[Any]], value)

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

      case ExecutableOp.ReferenceInstance(target, _, instance) =>
        StepResult.NewInstance(target, instance)

      case ExecutableOp.CustomOp(target, customDef) =>
        throw new DIException(s"No handle for CustomOp for $target, defs: $customDef", null)

      case ExecutableOp.WiringOp.CallProvider(target, _, associations) =>
        // TODO: here we depend on order
        val args = associations.associations.map {
          key =>
            map.get(key.wireWith) match {
              case Some(dep) =>
                dep
              case _ =>
                throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
                  s" but the dependency has not been initialized: Class: $target, dependency: $key")
            }
        }

        val instance = associations.provider.apply(args: _*)
        StepResult.NewInstance(target, instance)

      case ExecutableOp.WiringOp.InstantiateClass(target, wireable) =>
        val depMap = wireable.associations.map {
          key =>
            map.get(key.wireWith) match {
              case Some(dep) =>
                key.symbol -> dep
              case _ =>
                throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
                  s" but the dependency has not been initialized: Class: $target, dependency: $key")
            }
        }.toMap

        val targetType = wireable.instanceType
        val refUniverse = currentMirror
        val refClass = refUniverse.reflectClass(targetType.tpe.typeSymbol.asClass)
        val ctor = targetType.tpe.decl(universe.termNames.CONSTRUCTOR).asMethod
        val refCtor = refClass.reflectConstructor(ctor)

        val orderedArgs = ctor.paramLists.head.map {
          key => depMap(key)
        }

        val instance = refCtor.apply(orderedArgs: _*)
        StepResult.NewInstance(target, instance)

      case _: ExecutableOp.WiringOp.InstantiateTrait =>
        ???

      case _: ExecutableOp.WiringOp.InstantiateFactory =>
        ???

      case _: ExecutableOp.ProxyOp.InitProxies =>
        ???

      case _: ExecutableOp.ProxyOp.MakeProxy =>
        ???
    }
  }

}
