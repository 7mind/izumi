package izumi.distage.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax.*
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue.{MissingProxyAdapter, UnexpectedProvisionResult, UnsupportedProxyOpException}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, MonadicOp, ProxyOp, WiringOp}
import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameDispatcher
import izumi.distage.model.provisioning.proxies.ProxyProvider.DeferredInit
import izumi.distage.model.provisioning.proxies.{ProxyDispatcher, ProxyProvider}
import izumi.distage.model.provisioning.strategies.*
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.distage.model.reflection.*
import izumi.distage.provisioning.strategies.ProxyStrategyDefaultImpl.FakeSet
import izumi.reflect.TagK

/**
  * Limitations:
  *  - Will not work for any class which performs any operations on forwarding refs within constructor
  *  - Untested on constructors accepting primitive values, will fail most likely
  */
class ProxyStrategyDefaultImpl(
  proxyProvider: ProxyProvider,
  mirrorProvider: MirrorProvider,
) extends ProxyStrategyDefaultImplPlatformSpecific(proxyProvider, mirrorProvider)
  with ProxyStrategy {

  override def makeProxy[F[_]: TagK](
    context: ProvisioningKeyProvider,
    makeProxy: ProxyOp.MakeProxy,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    val cogenNotRequired = makeProxy.byNameAllowed

    F.maybeSuspend {
      for {
        proxyInstance <-
          if (cogenNotRequired) {
            val proxy = new ByNameDispatcher(makeProxy.target)
            Right(DeferredInit(proxy, proxy))
          } else {
            for {
              tpe <- proxyTargetType(makeProxy)
              _ <-
                if (!mirrorProvider.canBeProxied(tpe)) {
                  failCogenProxy(tpe, makeProxy)
                } else {
                  Right(())
                }
              proxy <- makeCogenProxy[F](context, tpe, makeProxy)
            } yield {
              proxy
            }
          }
      } yield {
        Seq(
          NewObjectOp.UseInstance(makeProxy.target, proxyInstance.proxy),
          NewObjectOp.UseInstance(proxyControllerKey(makeProxy.target), proxyInstance.dispatcher),
        )
      }
    }
  }

  override def initProxy[F[_]: TagK](
    context: ProvisioningKeyProvider,
    executor: OperationExecutor,
    initProxy: ProxyOp.InitProxy,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    val target = initProxy.proxy.target
    val key = proxyControllerKey(target)

    context.fetchUnsafe(key) match {
      case Some(dispatcher: ProxyDispatcher) =>
        executor
          .execute(context, initProxy.proxy.op)
          .flatMap {
            case Left(value) =>
              F.pure(Left(value))
            case Right(value) =>
              value.toList match {
                case NewObjectOp.UseInstance(_, instance) :: Nil =>
                  F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
                    .map(
                      _ =>
                        Right(
                          Seq(
                            NewObjectOp.UseInstance(initProxy.target, instance)
                          )
                        )
                    )
                case NewObjectOp.NewInstance(_, tpe, instance) :: Nil =>
                  F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
                    .map(
                      _ =>
                        Right(
                          Seq(
                            NewObjectOp.NewInstance(initProxy.target, tpe, instance)
                          )
                        )
                    )

                case (r @ NewObjectOp.NewResource(_, tpe, instance, _)) :: Nil =>
                  val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
                  F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
                    .map(
                      _ =>
                        Right(
                          Seq(
                            NewObjectOp.NewInstance(initProxy.target, tpe, instance),
                            NewObjectOp.NewFinalizer(target, finalizer),
                          )
                        )
                    )

                case r =>
                  F.pure(Left(UnexpectedProvisionResult(key, r)))
              }
          }

      case _ =>
        F.pure(Left(MissingProxyAdapter(key, initProxy)))
    }
  }

  protected def proxyTargetType(makeProxy: ProxyOp.MakeProxy): Either[ProvisionerIssue, SafeType] = {
    makeProxy.op match {
      case _: CreateSet =>
        // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
        Right(SafeType.get[FakeSet[?]])
      case op: WiringOp.CallProvider =>
        Right(op.target.tpe)
      case op: MonadicOp.AllocateResource =>
        Right(op.target.tpe)
      case op: MonadicOp.ExecuteEffect =>
        Right(op.target.tpe)
      case op: WiringOp.UseInstance =>
        Left(UnsupportedProxyOpException(op))
      case op: WiringOp.ReferenceKey =>
        Left(UnsupportedProxyOpException(op))
    }
  }

  protected def proxyControllerKey(m: DIKey): DIKey = {
    DIKey.ProxyControllerKey(m, SafeType.get[ProxyDispatcher])
  }

}

object ProxyStrategyDefaultImpl {
  // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
  private trait FakeSet[A] extends Set[A]
}
