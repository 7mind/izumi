package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions._
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.plan.ExecutableOp.{CreateSet, MonadicOp, ProxyOp, WiringOp}
import izumi.distage.model.provisioning.strategies.ProxyDispatcher.ByNameDispatcher
import izumi.distage.model.provisioning.strategies.ProxyProvider.{DeferredInit, ProxyContext, ProxyParams}
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.distage.model.reflection.universe.MirrorProvider
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.reflection.Tags.TagK

// CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
private[strategies] trait FakeSet[A] extends Set[A]

/**
  * Limitations:
  * - Will not work for any class which performs any operations on forwarding refs within constructor
  * - Untested on constructors accepting primitive values, will fail most likely
  */
class ProxyStrategyDefaultImpl
(
  proxyProvider: ProxyProvider,
  mirrorProvider: MirrorProvider,
) extends ProxyStrategy {

  override def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp] = {
    val cogenNotRequired = makeProxy.byNameAllowed

    val proxyInstance = if (cogenNotRequired) {
      val proxy = new ByNameDispatcher(makeProxy.target)
      DeferredInit(proxy, proxy)
    } else {
      val tpe = proxyTargetType(makeProxy)
      if (!mirrorProvider.canBeProxied(tpe)) {
        throw new UnsupportedOpException(s"Tried to make proxy of non-proxyable (final?) $tpe", makeProxy)
      }
      makeCogenProxy(context, tpe, makeProxy)
    }

    Seq(
      NewObjectOp.NewInstance(makeProxy.target, proxyInstance.proxy),
      NewObjectOp.NewInstance(proxyKey(makeProxy.target), proxyInstance.dispatcher),
    )
  }

  override def initProxy[F[_]: TagK](context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy)
                                    (implicit F: DIEffect[F]): F[Seq[NewObjectOp]] = {
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

  protected def makeCogenProxy(context: ProvisioningKeyProvider, tpe: SafeType, op: ProxyOp.MakeProxy): DeferredInit = {
    val runtimeClass = mirrorProvider.runtimeClass(tpe).getOrElse(throw new NoRuntimeClassException(op.target))

    val classConstructorParams = if (!hasDeps(tpe)) ProxyParams.Empty else {
//      // FIXME: Proxy classtag params ???
//      val params: Seq[Association.Parameter] = {
//        Nil
////        ???
//        // reflectionProvider.constructorParameters(tpe)
//      }
//
//      val args = params.map {
//        param =>
//          val value = param match {
//            case param if op.forwardRefs.contains(param.key) =>
//              // substitute forward references by `null`
//              null
//            case param =>
//              context.fetchKey(param.key, param.isByName) match {
//                case Some(v) =>
//                  v.asInstanceOf[AnyRef]
//                case None =>
//                  throw new MissingRefException(s"Proxy precondition failed: non-forwarding key expected to be in context but wasn't: ${param.key}", Set(param.key), None)
//              }
//          }
//
//          val parameterType: Class[_] = if (param.isByName) {
//            classOf[Function0[_]]
//          } else if (param.wasGeneric) {
//            classOf[AnyRef]
//          } else {
//            param.key.tpe.cls
//          }
//          (parameterType, value)
//      }

      val args: Array[(Class[_], AnyRef)] = runtimeClass.getConstructors.head.getParameterTypes.map(_ -> (null: AnyRef))
      val (argClasses, argValues) = args.unzip
      ProxyParams.Params(argClasses, argValues)
    }

    val proxyContext = ProxyContext(runtimeClass, op, classConstructorParams)

    proxyProvider.makeCycleProxy(op.target, proxyContext)
  }

  protected def hasDeps(tpe: SafeType): Boolean = {
    // FIXME: aadfhgadfgh ???
    val constructors = tpe.cls.getConstructors
    constructors.nonEmpty && !constructors.exists(_.getParameters.isEmpty)

//    false
//    tpe.use {
//      t =>
//        val constructors = t.decls.filter(_.isConstructor)
//        val hasTrivial = constructors.exists(_.asMethod.paramLists.forall(_.isEmpty))
//        val hasNoDependencies = constructors.isEmpty || hasTrivial
//        !hasNoDependencies
//    }
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
      case op: WiringOp.UseInstance =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
      case op: WiringOp.ReferenceKey =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
    }
  }

  protected def proxyKey(m: DIKey): DIKey = {
    DIKey.ProxyElementKey(m, SafeType.get[ProxyDispatcher])
  }

}

