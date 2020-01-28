package izumi.distage.testkit.services.scalatest.adapter

import distage.{DIKey, Locator, SafeType, TagK}
import izumi.distage.bootstrap.BootstrapLocator
import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.definition.{DIResource, LocatorDef}
import izumi.distage.model.effect.{DIApplicative, DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.provisioning.PlanInterpreter.Finalizer
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.testkit.services.scalatest.adapter.ExternalResourceProvider.{MemoizedInstance, PreparedShutdownRuntime}
import izumi.fundamentals.platform.cache.SyncCache
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks

import scala.annotation.unchecked.uncheckedVariance

@deprecated("Use dstest", "2019/Jul/18")
trait ExternalResourceProvider {
  def process(ctx: MemoizationContextId, ref: MemoizedInstance[Any]): Unit

  def isMemoized(ctx: MemoizationContextId, key: DIKey): Boolean

  def getMemoized(ctx: MemoizationContextId, key: DIKey): Option[Any]

  def size(ctx: MemoizationContextId): Int

  def size: Int

  def registerShutdownRuntime[F[_]: TagK](rt: => PreparedShutdownRuntime[F]): Unit
}

object ExternalResourceProvider {

  case class OrderedFinalizer[+F[_]](finalizer: Finalizer[F@uncheckedVariance], order: Int)

  case class MemoizedInstance[+F[_]](ref: IdentifiedRef, finalizer: Option[OrderedFinalizer[F@uncheckedVariance]])

  case class PreparedShutdownRuntime[+F[_]](runner: DIResourceBase[Identity, Locator], fType: SafeType, fTag: TagK[F @uncheckedVariance])

  object PreparedShutdownRuntime {
    def apply[F[_]: TagK](runner: DIResourceBase[Identity, Locator]) = new PreparedShutdownRuntime[F](runner, SafeType.getK[F], TagK[F])
  }

  object Null extends ExternalResourceProvider {

    override def process(memoizationContextId: MemoizationContextId, ref: MemoizedInstance[Any]): Unit = {
      Quirks.discard(memoizationContextId, ref)
    }

    override def isMemoized(ctx: MemoizationContextId, key: DIKey): Boolean = false

    override def getMemoized(ctx: MemoizationContextId, key: DIKey): Option[Any] = None

    override def size(ctx: MemoizationContextId): Int = {
      Quirks.discard(ctx)
      0
    }

    override def size: Int = 0

    override def registerShutdownRuntime[F[_]: TagK](rt: => PreparedShutdownRuntime[F]): Unit = {
      Quirks.forget(rt)
    }
  }

  def singleton[F[_]: TagK](memoize: IdentifiedRef => Boolean): Singleton[F] = new Singleton[F](memoize)

  class Singleton[F[_]: TagK](
                               memoize: IdentifiedRef => Boolean
                             ) extends ExternalResourceProvider {

    import Singleton.{MemoizationKey, cache, registerRT}

    private[this] final val keyF = SafeType.getK[F]

    override def process(ctx: MemoizationContextId, ref: MemoizedInstance[Any]): Unit = {
      if (memoize(ref.ref)) {
        cache.putIfNotExist(MemoizationKey(ctx, ref.ref.key, keyF), ref)
      }
    }

    override def isMemoized(ctx: MemoizationContextId, key: DIKey): Boolean = {
      cache.hasKey(MemoizationKey(ctx, key, keyF))
    }

    override def getMemoized(ctx: MemoizationContextId, key: DIKey): Option[Any] = {
      cache.get(MemoizationKey(ctx, key, keyF)).map(_.ref.value)
    }

    override def size(ctx: MemoizationContextId): Int = {
      cache.enumerate().count(_._1.ctx == ctx)
    }

    override def size: Int = {
      cache.size
    }

    override def registerShutdownRuntime[F1[_]: TagK](rt: => PreparedShutdownRuntime[F1]): Unit = {
      registerRT[F1](rt)
    }
  }

  object Singleton {

    case class MemoizationKey(ctx: MemoizationContextId, key: DIKey, fType: SafeType)

    private val runtimes = new SyncCache[SafeType, PreparedShutdownRuntime[Any]]()

    private val cache = new SyncCache[MemoizationKey, MemoizedInstance[Any]]()

    private val shutdownHook = new Thread(() => stop(), "termination-hook-memoizer")

    private val logger = TrivialLogger.make[BootstrapLocator]("izumi.distage.debug.memoizer")

    Runtime.getRuntime.addShutdownHook(shutdownHook)

    registerRT[Identity](
      PreparedShutdownRuntime[Identity](
        DIResource.liftF[Identity, Locator] {
          new LocatorDef {
            addImplicit[DIEffectRunner[Identity]]
            addImplicit[DIApplicative[Identity]]
            addImplicit[DIEffect[Identity]]
            addImplicit[DIEffectAsync[Identity]]
          }
        }
      )
    )

    private def registerRT[F[_]: TagK](rt: => PreparedShutdownRuntime[F]): Unit = {
      runtimes.putIfNotExist(SafeType.getK[F], rt)
    }

    /**
     * Fake HKT to use in-place of (unknown at compile-time) user effect type
     *
     * NOTE: DO NOT move this into an `object` or inside `stop()`, since then
     * scala-reflect will start generating TypeTags for it and break it. Whether or not
     * scala-reflect generates a TypeTag for an abstract member type is inconsistent
     * and depends on the type's placement
     * @see TagTest "Handle abstract types instead of parameters"
     * */
    private[Singleton] type FakeF[T]

    private def stop(): Unit = {

      runtimes.enumerate().foreach {
        case (rtType, rt) =>

          val effects = cache.enumerate()
            .filter {
              case (k, _) =>
                rtType == k.fType
            }
            .flatMap(_._2.finalizer.toSeq)
            .sortBy(_.order)
            .map(_.finalizer)
            .map {
              fin =>
                fin.asInstanceOf[Finalizer[FakeF]]
            }
          import DIEffect.syntax._

          implicit val effectTagK: TagK[FakeF] = rt.fTag.asInstanceOf[TagK[FakeF]]
          Quirks.discard(effectTagK)
          assert(!effectTagK.toString.contains("FakeF"))

          val effectTag = SafeType.get[DIEffect[FakeF]]
          val runnerTag = SafeType.get[DIEffectRunner[FakeF]]

          if (effects.nonEmpty) {
            rt.runner.use {
              locator =>

                val shutdown = for {
                  eff <- locator.instances.find(_.key.tpe == effectTag).map(_.value.asInstanceOf[DIEffect[FakeF]])
                  ru <- locator.instances.find(_.key.tpe == runnerTag).map(_.value.asInstanceOf[DIEffectRunner[FakeF]])
                } yield {
                  implicit val F: DIEffect[FakeF] = eff
                  implicit val runner: DIEffectRunner[FakeF] = ru
                  runner.run {
                    for {
                      _ <- effects.foldLeft(F.maybeSuspend(logger.log(s"Running finalizers in effect type ${rt.fType}..."))) {
                        case (acc, f) =>
                          acc.guarantee {
                            for {
                              _ <- F.maybeSuspend(logger.log(s"Closing ${f.key}..."))
                              _ <- F.suspendF(f.effect())
                            } yield ()
                          }
                      }
                      _ <- F.maybeSuspend(logger.log(s"Finished finalizers in effect type ${rt.fType}!"))
                    } yield ()
                  }
                }

                shutdown match {
                  case Some(_) =>
                  case None =>
                    logger.log(s"Failed to build shutdown context. Required keys: $effectTag, $runnerTag, Locator content: ${locator.index.keySet}")
                }

            }
          }
      }

    }


  }

}
