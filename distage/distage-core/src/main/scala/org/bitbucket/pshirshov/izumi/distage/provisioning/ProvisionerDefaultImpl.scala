package org.bitbucket.pshirshov.izumi.distage.provisioning

import net.sf.cglib.proxy.Enhancer
import org.bitbucket.pshirshov.izumi.distage.Locator
import org.bitbucket.pshirshov.izumi.distage.model.exceptions._
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.{ProxyOp, SetOp, WiringOp}
import org.bitbucket.pshirshov.izumi.distage.model.plan.{ExecutableOp, FinalPlan}
import org.bitbucket.pshirshov.izumi.distage.model.{DIKey, EqualitySafeType}

import scala.collection.mutable
import scala.reflect.runtime._
import scala.util.{Failure, Success, Try}


class ProvisionerDefaultImpl(
                              provisionerHook: ProvisionerHook
                              , provisionerIntrospector: ProvisionerIntrospector
                            ) extends Provisioner {
  override def provision(plan: FinalPlan, parentContext: Locator): ProvisionImmutable = {
    val activeProvision = ProvisionActive()

    val provisions = plan.steps.foldLeft(activeProvision) {
      case (active, step) =>
        execute(LocatorContext(active, parentContext), step).foldLeft(active) {
          case (acc, result) =>
            Try(interpretResult(active, result)) match {
              case Failure(f) =>
                throw new DIException(s"Provisioning unexpectedly failed on result handling for $step => $result", f)
              case _ =>
                acc
            }
        }

    }

    ProvisionImmutable(provisions.instances, provisions.imports)
  }

  private def interpretResult(active: ProvisionActive, result: OpResult): Unit = {
    result match {

      case OpResult.NewImport(target, value) =>
        if (active.imports.contains(target)) {
          throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
        }
        active.imports += (target -> value)

      case OpResult.NewInstance(target, value) =>
        if (active.instances.contains(target)) {
          throw new DuplicateInstancesException(s"Cannot continue, key is already in context", target)
        }
        active.instances += (target -> value)

      case OpResult.SetElement(set, instance) =>
        if (set == instance) {
          throw new DIException(s"Pathological case. Tried to add set into itself: $set", null)
        }
        set += instance
    }
  }

  private def execute(context: ProvisioningContext, step: ExecutableOp): Seq[OpResult] = {
    step match {
      case op: ExecutableOp.ImportDependency =>
        import op._
        context.importKey(target) match {
          case Some(v) =>
            Seq(OpResult.NewImport(target, v))
          case _ =>
            throw new MissingInstanceException(s"Instance is not available in the context: $target. " +
              s"required by refs: $references", target)
        }

      case ExecutableOp.WiringOp.ReferenceInstance(target, wiring) =>
        Seq(OpResult.NewInstance(target, wiring.instance))

      case op: ExecutableOp.SetOp.CreateSet =>
        Seq(makeSet(context, op))

      case op: SetOp.AddToSet =>
        Seq(addToSet(context, op))

      case op: ExecutableOp.WiringOp.CallProvider =>
        Seq(callProvider(context, op))

      case op: ExecutableOp.WiringOp.InstantiateClass =>
        Seq(instantiateClass(context, op))

      case ExecutableOp.CustomOp(target, customDef) =>
        throw new DIException(s"No handle for CustomOp for $target, defs: $customDef", null)

      case t: ExecutableOp.WiringOp.InstantiateTrait =>
        makeTrait(t)

      case f: WiringOp.InstantiateFactory =>
        makeFactory(context, f)

      case m: ExecutableOp.ProxyOp.MakeProxy =>
        makeProxy(m)

      case i: ExecutableOp.ProxyOp.InitProxy =>
        initProxy(context, i)
    }
  }

  private def makeTrait(t: WiringOp.InstantiateTrait) = {
    val instance = null
    Seq(OpResult.NewInstance(t.target, instance))
  }

  private def makeFactory(context: ProvisioningContext, f: WiringOp.InstantiateFactory) = {
    // at this point we definitely have all the dependencies instantiated
    val allRequiredKeys = f.wiring.associations.map(_.wireWith).toSet
    val executor = mkExecutor(context.narrow(allRequiredKeys))
    val instance = null
    Seq(OpResult.NewInstance(f.target, instance))
  }

  private def initProxy(context: ProvisioningContext, i: ProxyOp.InitProxy) = {
    val instance = execute(context, i.proxy.op)

    val key = proxyKey(i.target)

    context.fetchKey(key) match {
      case Some(adapter: CglibRefDispatcher) =>
        adapter.reference.set(instance)
      case None =>
        throw new DIException(s"Cannot get adapter $key for $i", null)
    }


    Seq()
  }

  private def makeProxy(m: ProxyOp.MakeProxy) = {
    val tpe = m.op match {
      case op: WiringOp.InstantiateTrait =>
        op.wiring.instanceType
      case op: WiringOp.InstantiateClass =>
        op.wiring.instanceType
      case op: WiringOp.InstantiateFactory =>
        op.wiring.factoryType
      case op =>
        throw new DIException(s"Operation unsupported by proxy mechanism: $op", null)
    }

    val constructors = tpe.tpe.decls.filter(_.isConstructor)
    val constructable = constructors.forall(_.asMethod.paramLists.forall(_.isEmpty))
    if (!constructable) {
      throw new DIException(s"Failed to instantiate proxy ${m.target}. All the proxy constructors must be zero-arg though we have $constructors", null)
    }

    val refUniverse = currentMirror
    val refClass = refUniverse.reflectClass(tpe.tpe.typeSymbol.asClass)
    val runtimeClass = currentMirror.runtimeClass(tpe.tpe)
    val interfaces = runtimeClass.getInterfaces

    val dispatcher = new CglibRefDispatcher(m.target)
    val enhancer = new Enhancer()
    enhancer.setSuperclass(runtimeClass)
    enhancer.setCallback(dispatcher)


    Try(enhancer.create()) match {
      case Success(proxyInstance) =>
        Seq(
          OpResult.NewInstance(m.target, proxyInstance)
          , OpResult.NewInstance(proxyKey(m.target), dispatcher)
        )
      case Failure(f) =>
        throw new DIException(s"Failed to instantiate proxy. Probably it's a pathologic cycle of concrete classes. Proxy operation: $m. ", f)
    }
  }

  private def makeSet(context: ProvisioningContext, op: ExecutableOp.SetOp.CreateSet) = {
    import op._
    // target is guaranteed to be a Set
    val scalaCollectionSetType = EqualitySafeType.get[collection.Set[_]]
    val erasure = scalaCollectionSetType.tpe.typeSymbol

    if (!tpe.tpe.baseClasses.contains(erasure)) {
      throw new IncompatibleTypesException("Tried to create make a Set with a non-Set type! " +
        s"For $target expected $tpe to be a sub-class of $scalaCollectionSetType, but it isn't!"
        , scalaCollectionSetType
        , tpe)
    }

    OpResult.NewInstance(target, mutable.HashSet[Any]())
  }

  private def addToSet(context: ProvisioningContext, op: ExecutableOp.SetOp.AddToSet) = {
    // value is guaranteed to have already been instantiated or imported
    val targetElement = context.fetchKey(op.element) match {
      case Some(value) =>
        value
      case _ =>
        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
          s" but the instance has not been initialized! Set: ${op.target}, instance: ${op.element}")
    }

    // set is guaranteed to have already been added
    val targetSet = context.fetchKey(op.target) match {
      case Some(set: mutable.HashSet[_]) =>
        set
      case Some(somethingElse) =>
        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
          s" but target Set is not a Set! It's ${somethingElse.getClass.getName}")

      case _ =>
        throw new InvalidPlanException(s"The impossible happened! Tried to add instance to Set Binding," +
          s" but Set has not been initialized! Set: ${op.target}, instance: ${op.element}")
    }

    OpResult.SetElement(targetSet.asInstanceOf[mutable.HashSet[Any]], targetElement)
  }

  private def callProvider(context: ProvisioningContext, op: ExecutableOp.WiringOp.CallProvider) = {
    import op._
    // TODO: here we depend on order
    val args = wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith) match {
          case Some(dep) =>
            dep
          case _ =>
            throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: Class: $target, dependency: $key")
        }
    }

    val instance = wiring.provider.apply(args: _*)
    OpResult.NewInstance(target, instance)
  }

  private def instantiateClass(context: ProvisioningContext, op: ExecutableOp.WiringOp.InstantiateClass) = {
    import op._
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
  }

  private def mkExecutor(context: ProvisioningContext) = {
    new OperationExecutor {
      override def execute(step: ExecutableOp): Seq[OpResult] = {
        ProvisionerDefaultImpl.this.execute(context, step)
      }
    }
  }

  private def proxyKey(m: DIKey) = {
    DIKey.ProxyElementKey(m, EqualitySafeType.get[CglibRefDispatcher])
  }
}
