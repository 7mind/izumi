package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.Locator
import org.bitbucket.pshirshov.izumi.di.model.exceptions._
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, FinalPlan}
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}

import scala.collection.{mutable, _}
import scala.reflect.runtime._

sealed trait StepResult {}

object StepResult {

  case class NewInstance(key: DIKey, value: Any) extends StepResult

  case class ExtendSet(set: mutable.HashSet[Any], instance: Any) extends StepResult

}

class ProvisionerDefaultImpl extends Provisioner {
  override def provision(dIPlan: FinalPlan, parentContext: Locator): Map[DIKey, Any] =
    dIPlan.steps.foldLeft(mutable.HashMap[DIKey, Any]()) {
      case (map, step) =>
        val result = stepToUpdate(parentContext, map, step)
        result match {
          case StepResult.NewInstance(target, value) =>
            if (map.contains(target)) {
              throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
            }
            map += (target -> value)
          case StepResult.ExtendSet(set, instance) =>
            set += instance
            map
        }

    }

  private def stepToUpdate(parentContext: Locator, map: Map[DIKey, Any], step: ExecutableOp): StepResult = {
    step match {
      case ExecutableOp.ImportDependency(target, references) =>
        parentContext.lookupInstance[Any](target) match {
          case Some(v) =>
            StepResult.NewInstance(target, v)
          case _ =>
            throw new MissingInstanceException(s"Instance is not available in the context: $target. " +
              s"required by refs: $references", target)
        }

      case ExecutableOp.CreateSet(target, targetType) =>
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

      case ExecutableOp.AddToSet(target, key) =>
        // value is guaranteed to have already been instantiated or imported
        map.get(key) match {
          case Some(value) =>
            // set is guaranteed to have already been added
            map.get(target) match {
              case Some(set: mutable.HashSet[_]) =>
                StepResult.ExtendSet(set.asInstanceOf[mutable.HashSet[Any]], value)

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

      case ExecutableOp.CallProvider(target, _, associations, function) =>
        // TODO: here we depend on order
        val args = associations.map {
          key =>
            map.get(key.wireWith) match {
              case Some(dep) =>
                dep
              case _ =>
                throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
                  s" but the dependency has not been initialized: Class: $target, dependency: $key")
            }
        }

        val instance = function.apply(args :_*)
        StepResult.NewInstance(target, instance)

      case ExecutableOp.InstantiateClass(target, targetType, associations) =>
        val depMap = associations.map {
          key =>
            map.get(key.wireWith) match {
              case Some(dep) =>
                key.symbol -> dep
              case _ =>
                throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
                  s" but the dependency has not been initialized: Class: $target, dependency: $key")
            }
        }.toMap

        val refUniverse = currentMirror
        val refClass = refUniverse.reflectClass(targetType.tpe.typeSymbol.asClass)
        val ctor = targetType.tpe.decl(universe.termNames.CONSTRUCTOR).asMethod
        val refCtor = refClass.reflectConstructor(ctor)

        val orderedArgs = ctor.paramLists.head.map {
          key => depMap(key)
        }

        val instance = refCtor.apply(orderedArgs: _*)
        StepResult.NewInstance(target, instance)

      case _: ExecutableOp.InstantiateTrait =>
        ???

      case _: ExecutableOp.InstantiateFactory =>
        ???

      case _: ExecutableOp.InitProxies =>
        ???

      case _: ExecutableOp.MakeProxy =>
        ???
    }
  }

}
