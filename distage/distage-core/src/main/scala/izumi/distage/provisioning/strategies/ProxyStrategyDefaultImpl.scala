package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions._
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ProxyOp, WiringOp}
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import izumi.distage.model.reflection.{ReflectionProvider, SymbolIntrospector}

// CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
trait FakeSet[A] extends Set[A]

/**
  * Limitations:
  * - Will not work for any class which performs any operations on forwarding refs within constructor
  * - Untested on constructors accepting primitive values, will fail most likely
  */
class ProxyStrategyDefaultImpl(
                                reflectionProvider: ReflectionProvider.Runtime
                                , introspector: SymbolIntrospector.Runtime
                                , proxyProvider: ProxyProvider
                                , mirror: MirrorProvider
                              ) extends ProxyStrategy {
  def initProxy[F[_]: TagK](context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy)(implicit F: DIEffect[F]): F[Seq[NewObjectOp]] = {
    val target = initProxy.target
    val key = proxyKey(target)
    context.fetchUnsafe(key) match {
      case Some(dispatcher: ProxyDispatcher) =>
        executor.execute(context, initProxy.proxy.op).flatMap(_.toList match {
          case NewObjectOp.NewInstance(_, instance) :: Nil =>
            F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
              .map(_ => Seq.empty)
          case (r@NewObjectOp.NewResource(_, instance, _)) :: Nil =>
            val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
            F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
              .map(_ => Seq(NewObjectOp.NewFinalizer(target, finalizer)))
          case r =>
            throw new UnexpectedProvisionResultException(s"Unexpected operation result for $key: $r, expected a single NewInstance!", r)
        })
      case _ =>
        throw new MissingProxyAdapterException(s"Cannot get dispatcher $key for $initProxy", key, initProxy)
    }
  }

  def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp] = {
    val cogenNotRequired = makeProxy.byNameAllowed

    val proxyInstance = if (cogenNotRequired) {
      val proxy = new ByNameDispatcher(makeProxy.target)
      DeferredInit(proxy, proxy)
    } else {
      val tpe = proxyTargetType(makeProxy)
      if (!introspector.canBeProxied(tpe)) {
        throw new UnsupportedOpException(s"Tried to make proxy of non-proxyable (final?) $tpe", makeProxy)
      }
      makeCogenProxy(context, tpe, makeProxy)
    }

    Seq(
      NewObjectOp.NewInstance(makeProxy.target, proxyInstance.proxy)
      , NewObjectOp.NewInstance(proxyKey(makeProxy.target), proxyInstance.dispatcher)
    )
  }


  protected def makeCogenProxy(context: ProvisioningKeyProvider, tpe: SafeType, makeProxy: ProxyOp.MakeProxy): DeferredInit = {
    val params = if (hasDeps(tpe)) {
      val params = reflectionProvider.constructorParameters(tpe)

      val args = params.map {
        param =>
          val value = param match {
            case p if makeProxy.forwardRefs.contains(p.wireWith) =>
              null

            case p =>
              context.fetchKey(p.wireWith, p.isByName) match {
                case Some(v) =>
                  v.asInstanceOf[AnyRef]
                case None =>
                  throw new MissingRefException(s"Proxy precondition failed: non-forwarding key expected to be in context but wasn't: ${p.wireWith}", Set(p.wireWith), None)
              }
          }

          val parameterType = if (param.isByName) {
            import u._
            typeOf[() => Any]
          } else if (param.wasGeneric) {
            import u._
            typeOf[AnyRef]
          } else {
            param.wireWith.tpe.use(identity)
          }
          (parameterType, value)
      }

      val argClasses = args.map(_._1)
        .map {
          t =>
          mirror.runtimeClass(t).getOrElse(throw new NoRuntimeClassException(makeProxy.target, SafeType(t)))
        }
        .toArray
      val argValues = args.map(_._2).toArray
      ProxyParams.Params(argClasses, argValues)
    } else { // this shouldn't happen anymore
      ProxyParams.Empty
    }

    val runtimeClass = tpe.use(mirror.runtimeClass).getOrElse(throw new NoRuntimeClassException(makeProxy.target))
    val proxyContext = ProxyContext(runtimeClass, makeProxy, params)

    val proxyInstance = proxyProvider.makeCycleProxy(CycleContext(makeProxy.target), proxyContext)
    proxyInstance
  }

  protected def hasDeps(tpe: RuntimeDIUniverse.SafeType): Boolean = {
    tpe.use {
      t =>
        val constructors = t.decls.filter(_.isConstructor)
        val hasTrivial = constructors.exists(_.asMethod.paramLists.forall(_.isEmpty))
        val hasNoDependencies = constructors.isEmpty || hasTrivial
        !hasNoDependencies
    }
  }

  protected def proxyTargetType(makeProxy: ProxyOp.MakeProxy): SafeType = {
    makeProxy.op match {
      case _: CreateSet =>
        // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
        //op.target.symbol
        SafeType.get[FakeSet[_]]
      case op: WiringOp.CallProvider =>
        op.target.tpe
      case op: WiringOp.CallFactoryProvider =>
        op.target.tpe
      case op: MonadicOp.AllocateResource =>
        op.target.tpe
      case op: MonadicOp.ExecuteEffect =>
        op.target.tpe
      case op: WiringOp.ReferenceInstance =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
      case op: WiringOp.ReferenceKey =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
    }
  }

  protected def proxyKey(m: DIKey): DIKey = {
    DIKey.ProxyElementKey(m, SafeType.get[ProxyDispatcher])
  }

}

