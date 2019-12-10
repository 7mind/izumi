//package izumi.distage.provisioning.strategies
//
//import izumi.distage.model.effect.DIEffect
//import izumi.distage.model.effect.DIEffect.syntax._
//import izumi.distage.model.exceptions._
//import izumi.distage.model.plan.ExecutableOp.{CreateSet, MonadicOp, ProxyOp, WiringOp}
//import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameDispatcher
//import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext, ProxyParams}
//import izumi.distage.model.provisioning.proxies.{ProxyDispatcher, ProxyProvider}
//import izumi.distage.model.provisioning.strategies._
//import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider, WiringExecutor}
//import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
//import izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
//import izumi.distage.provisioning.strategies.ProxyStrategyDefaultImpl.FakeSet
//import izumi.fundamentals.platform.language.unused
//import izumi.fundamentals.reflection.Tags.TagK
//import izumi.fundamentals.reflection.TypeUtil
//
///**
//  * Limitations:
//  * - Will not work for any class which performs any operations on forwarding refs within constructor
//  * - Untested on constructors accepting primitive values, will fail most likely
//  */
//class ProxyStrategyDefaultImpl
//(
//  proxyProvider: ProxyProvider,
//  mirrorProvider: MirrorProvider,
//) extends ProxyStrategy {
//
//  override def makeProxy(context: ProvisioningKeyProvider, @unused executor: WiringExecutor, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp] = {
//    val cogenNotRequired = makeProxy.byNameAllowed
//
//    val proxyInstance = if (cogenNotRequired) {
//      val proxy = new ByNameDispatcher(makeProxy.target)
//      DeferredInit(proxy, proxy)
//    } else {
//      val tpe = proxyTargetType(makeProxy)
//      if (!mirrorProvider.canBeProxied(tpe)) {
//        throw new UnsupportedOpException(s"Tried to make proxy of non-proxyable (final?) $tpe", makeProxy)
//      }
//      makeCogenProxy(context, tpe, makeProxy)
//    }
//
//    Seq(
//      NewObjectOp.NewInstance(makeProxy.target, proxyInstance.proxy),
//      NewObjectOp.NewInstance(proxyKey(makeProxy.target), proxyInstance.dispatcher),
//    )
//  }
//
//  override def initProxy[F[_]: TagK](context: ProvisioningKeyProvider,
//                                     executor: OperationExecutor,
//                                     initProxy: ProxyOp.InitProxy,
//                                    )(implicit F: DIEffect[F]): F[Seq[NewObjectOp]] = {
//    val target = initProxy.target
//    val key = proxyKey(target)
//
//    context.fetchUnsafe(key) match {
//      case Some(dispatcher: ProxyDispatcher) =>
//        executor.execute(context, initProxy.proxy.op).flatMap(_.toList match {
//
//          case NewObjectOp.NewInstance(_, instance) :: Nil =>
//            F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
//              .map(_ => Seq.empty)
//
//          case (r@NewObjectOp.NewResource(_, instance, _)) :: Nil =>
//            val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
//            F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
//              .map(_ => Seq(NewObjectOp.NewFinalizer(target, finalizer)))
//
//          case r =>
//            throw new UnexpectedProvisionResultException(s"Unexpected operation result for $key: $r, expected a single NewInstance!", r)
//        })
//      case _ =>
//        throw new MissingProxyAdapterException(s"Cannot get dispatcher $key for $initProxy", key, initProxy)
//    }
//  }
//
//  protected def makeCogenProxy(context: ProvisioningKeyProvider, tpe: SafeType, op: ProxyOp.MakeProxy): DeferredInit = {
//    val runtimeClass = mirrorProvider.runtimeClass(tpe).getOrElse(throw new NoRuntimeClassException(op.target))
//
//    val classConstructorParams = if (noArgsConstructor(tpe)) {
//      ProxyParams.Empty
//    } else {
//      val allArgsAsNull: Array[(Class[_], Any)] = {
//        op.op match {
//          case WiringOp.CallProvider(_, Wiring.SingletonWiring.Function(provider, params), _) if provider.isGenerated =>
//            // for generated constructors, try to fetch known dependencies from the object graph
//            params.map(fetchNonforwardRefParamWithClass(context, op.forwardRefs, _)).toArray
//          case _ =>
//            // otherwise fill everything with nulls
//            runtimeClass.getConstructors.head.getParameterTypes
//              .map(clazz => clazz -> TypeUtil.defaultValue(clazz))
//        }
//      }
//      val (argClasses, argValues) = allArgsAsNull.unzip
//      ProxyParams.Params(argClasses, argValues)
//    }
//
//    val proxyContext = ProxyContext(runtimeClass, op, classConstructorParams)
//
//    proxyProvider.makeCycleProxy(op.target, proxyContext)
//  }
//
//  private def fetchNonforwardRefParamWithClass(context: ProvisioningKeyProvider, forwardRefs: Set[DIKey], param: RuntimeDIUniverse.Association.Parameter): (Class[_], Any) = {
//    val clazz: Class[_] = if (param.isByName) {
//      classOf[Function0[_]]
//    } else if (param.wasGeneric) {
//      classOf[Any]
//    } else {
//      param.key.tpe.cls
//    }
//
//    val value = param match {
//      case param if forwardRefs.contains(param.key) =>
//        // substitute forward references by `null`
//        TypeUtil.defaultValue(param.key.tpe.cls)
//      case param =>
//        context.fetchKey(param.key, param.isByName) match {
//          case Some(v) =>
//            v.asInstanceOf[Any]
//          case None =>
//            throw new MissingRefException(s"Proxy precondition failed: non-forwarding key expected to be in context but wasn't: ${param.key}", Set(param.key), None)
//        }
//    }
//
//    (clazz, value)
//  }
//  protected def noArgsConstructor(tpe: SafeType): Boolean = {
//    val constructors = tpe.cls.getConstructors
//    constructors.isEmpty || constructors.exists(_.getParameters.isEmpty)
//  }
//
//  protected def proxyTargetType(makeProxy: ProxyOp.MakeProxy): SafeType = {
//    makeProxy.op match {
//      case _: CreateSet =>
//        // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
//        SafeType.get[FakeSet[_]]
//      case op: WiringOp.CallProvider =>
//        op.target.tpe
//      case op: MonadicOp.AllocateResource =>
//        op.target.tpe
//      case op: MonadicOp.ExecuteEffect =>
//        op.target.tpe
//      case op: WiringOp.UseInstance =>
//        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
//      case op: WiringOp.ReferenceKey =>
//        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
//    }
//  }
//
//  protected def proxyKey(m: DIKey): DIKey = {
//    DIKey.ProxyElementKey(m, SafeType.get[ProxyDispatcher])
//  }
//
//}
//
//object ProxyStrategyDefaultImpl {
//  // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
//  private trait FakeSet[A] extends Set[A]
//}
