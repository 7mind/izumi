package org.bitbucket.pshirshov.izumi.di.provisioning
import org.bitbucket.pshirshov.izumi.di.Locator
import org.bitbucket.pshirshov.izumi.di.model.exceptions.{DIException, IncompatibleTypesException, MissingInstanceException}
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, FinalPlan}
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}

import scala.collection.{mutable, _}
import scala.reflect.runtime._

class ProvisionerDefaultImpl extends Provisioner {

  override def provision(dIPlan: FinalPlan, parentContext: Locator): Map[DIKey, Any] =
    dIPlan.steps.foldLeft(mutable.HashMap[DIKey, Any]()) {
      case (map, step) => step match {

        case ExecutableOp.ImportDependency(target, references) =>
          parentContext.lookupInstance[Any](target) match {
            case Some(v) =>
              map += (target -> v)
            case _ =>
              throw new MissingInstanceException(s"Instance is not available in the context: $target. " +
                s"required by refs: $references", target)
          }

        case ExecutableOp.CreateSet(target, targetType) =>
          // target is guaranteed to be a Set
          val `scala.collection.Set` = EqualitySafeType.get[Set[_]] // typeTag[Set[_]].tpe.typeSymbol
          if (targetType.symbol.baseClasses.contains(`scala.collection.Set`.symbol)) {
            map += (target -> mutable.HashSet[Any]())
          } else {
            throw new IncompatibleTypesException("Tried to create make a Set with a non-Set type! " +
              s"For $target expected $targetType to be a sub-class of ${`scala.collection.Set`}, but it isn't!"
              , `scala.collection.Set`
              , targetType)
          }

        case ExecutableOp.AddToSet(target, key) =>
          // value is guaranteed to have already been instantiated or imported
          map.get(key) match {
            case Some(value) =>
              // set is guaranteed to have already been added
              map.get(target) match {
                case Some(set: mutable.HashSet[_]) =>
                  set.asInstanceOf[mutable.HashSet[Any]] += value
                  map
                case Some(somethingElse) =>
                  throw new DIException(s"The impossible happened! Tried to add instance to Set Binding," +
                    s" but target Set is not a Set! It's ${somethingElse.getClass.getName}", null)
                case _ =>
                  throw new DIException(s"The impossible happened! Tried to add instance to Set Binding," +
                    s" but Set has not been initialized! Set: $target, instance: $key", null)
              }
            case _ =>
              throw new DIException(s"The impossible happened! Tried to add instance to Set Binding," +
                s" but the instance has not been initialized! Set: $target, instance: $key", null)
          }

        case ExecutableOp.ReferenceInstance(target, _, instance) =>
          map += (target -> instance)

        case ExecutableOp.CustomOp(target, customDef) =>
          throw new DIException(s"No handle for CustomOp for $target, defs: $customDef", null)

        case ExecutableOp.InstantiateClass(target, targetType, associations) =>
          val depMap = associations.map {
            key => map.get(key.wireWith) match {
              case Some(dep) =>
                key.symbol -> dep
              case _ =>
                throw new DIException(s"The impossible happened! Tried to instantiate class," +
                  s" but the dependency has not been initialized: Class: $target, dependency: $key", null)
            }
          }.toMap
          val refUniverse = currentMirror
          val refClass = refUniverse.reflectClass(targetType.symbol.typeSymbol.asClass)
          val ctor = targetType.symbol.decl(universe.termNames.CONSTRUCTOR).asMethod
          val refCtor = refClass.reflectConstructor(ctor)

          val orderedArgs = ctor.paramLists.head.map {
            key => depMap(key)
          }

          val instance = refCtor.apply(orderedArgs: _*)

          map += (target -> instance)

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
