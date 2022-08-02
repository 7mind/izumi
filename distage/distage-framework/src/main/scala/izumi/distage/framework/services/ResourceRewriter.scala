package izumi.distage.framework.services

import izumi.distage.framework.services.ResourceRewriter.RewriteRules
import izumi.distage.model.definition.*
import izumi.distage.model.definition.Binding.{SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.ImplDef.DirectImplDef
import izumi.distage.model.definition.Lifecycle.makeSimple
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.logstage.api.IzLogger

import java.util.concurrent.{ExecutorService, TimeUnit}

/**
  * Rewrites bindings implemented with `_ <: AutoCloseable` into resource bindings that automatically close the implementation closeable.
  *
  * {{{
  *   class XImpl extends AutoCloseable
  *   make[X].from[XImpl]
  * }}}
  *
  * becomes:
  *
  * {{{
  *   make[X].fromResource {
  *    ClassConstructor[XImpl].map(distage.Lifecycle.fromAutoCloseable(_))
  *   }
  * }}}
  *
  * Will produce warnings for all rewritten bindings, so better explicitly use `.fromResource`!
  */
class ResourceRewriter(
  logger: IzLogger,
  rules: RewriteRules,
) extends PlanningHook {

  import ResourceRewriter.*
  import RewriteResult.*

  override def hookDefinition(definition: ModuleBase): ModuleBase = {
    if (rules.applyRewrites) {
      // this is a planning hotspot so, we microoptimize here
      val acTag = SafeType.get[AutoCloseable]
      val acResTag = SafeType.get[Lifecycle[Identity, AutoCloseable]]
      val esTag = SafeType.get[ExecutorService]
      val esResTag = SafeType.get[Lifecycle[Identity, ExecutorService]]

      definition
        .flatMap(rewrite[AutoCloseable](acTag, acResTag)(fromAutoCloseable(logger, _)))
        .flatMap(rewrite[ExecutorService](esTag, esResTag)(fromExecutorService(logger, _)))
    } else definition
  }

  private def rewrite[TGT](tgt: SafeType, resourceType: SafeType)(convert: TGT => Lifecycle[Identity, TGT])(b: Binding): Seq[Binding] = {
//    val tgt = SafeType.get[TGT]
//    val resourceType = SafeType.get[Lifecycle[Identity, TGT]]
    b match {
      case implBinding: Binding.ImplBinding =>
        implBinding match {
          case binding: Binding.SingletonBinding[?] =>
            rewriteImpl(convert, binding.key, binding.origin, binding.implementation, tgt, resourceType) match {
              case ReplaceImpl(newImpl) =>
                logger.info(s"Adapting ${binding.key} defined at ${binding.origin} as ${tgt -> "type"}")
                Seq(finish(binding, newImpl))
              case DontChange =>
                Seq(binding)
            }

          case binding: Binding.SetElementBinding =>
            rewriteImpl(convert, binding.key, binding.origin, binding.implementation, tgt, resourceType) match {
              case ReplaceImpl(newImpl) =>
                logger.info(s"Adapting set element ${binding.key} defined at ${binding.origin} as ${tgt -> "type"}")
                Seq(finish(binding, newImpl))
              case RewriteResult.DontChange =>
                Seq(binding)
            }
        }

      case binding: Binding.SetBinding =>
        Seq(binding)
    }
  }

  private def rewriteImpl[TGT](
    convert: TGT => Lifecycle[Identity, TGT],
    key: DIKey,
    origin: SourceFilePosition,
    implementation: ImplDef,
    tgt: SafeType,
    resourceType: SafeType,
  ): RewriteResult = {
    implementation match {
      case implDef: ImplDef.DirectImplDef =>
        val implType = implDef.implType
        if (tgt.cls.isAssignableFrom(implType.cls) && // performance optimization
          implType <:< tgt) {
          implDef match {
            case _: ImplDef.ReferenceImpl =>
              DontChange

            case _: ImplDef.InstanceImpl =>
              logger.warn(
                s"Instance binding for $key defined at $origin is <:< ${tgt -> "type"}, but it will NOT be finalized, because we assume it's defined for outer scope!!! Because it's not an explicit Lifecycle (define as function binding to force conversion)"
              )
              DontChange

            case ImplDef.ProviderImpl(_, function) =>
              val newImpl = function.unsafeMap(resourceType, (instance: Any) => convert(instance.asInstanceOf[TGT]))
              ReplaceImpl(ImplDef.ProviderImpl(resourceType, newImpl))
          }
        } else {
          DontChange
        }
      case implDef: ImplDef.RecursiveImplDef =>
        implDef match {
          case _: ImplDef.EffectImpl =>
            if (tgt.cls.isAssignableFrom(implDef.implType.cls) // performance optimization
              && implDef.implType <:< tgt) {
              logger.error(
                s"Effect entity $key defined at $origin is ${tgt -> "type"}, but it will NOT be finalized!!! You must explicitly wrap it into resource using Lifecycle.fromAutoCloseable/fromExecutorService"
              )
            }
            DontChange

          case _: ImplDef.ResourceImpl =>
            DontChange
        }
    }
  }

  private def finish(original: SingletonBinding[DIKey], newImpl: DirectImplDef): Binding = {
    val res = ImplDef.ResourceImpl(original.implementation.implType, SafeType.identityEffectType, newImpl)
    original.copy(implementation = res)
  }

  private def finish(original: SetElementBinding, newImpl: DirectImplDef): Binding = {
    val res = ImplDef.ResourceImpl(original.implementation.implType, SafeType.identityEffectType, newImpl)
    original.copy(implementation = res)
  }

}

object ResourceRewriter {

  sealed trait RewriteResult
  object RewriteResult {
    final case class ReplaceImpl(newImpl: DirectImplDef) extends RewriteResult
    case object DontChange extends RewriteResult
  }

  final case class RewriteRules(applyRewrites: Boolean = false)

  /** Like [[Lifecycle.fromAutoCloseable]], but with added logging */
  def fromAutoCloseable[A <: AutoCloseable](logger: IzLogger, acquire: => A): Lifecycle[Identity, A] = {
    makeSimple(acquire) {
      ac =>
        logger.info(s"Closing $ac...")
        ac.close()
    }
  }

  /** Like [[Lifecycle.fromExecutorService]], but with added logging */
  def fromExecutorService[A <: ExecutorService](logger: IzLogger, acquire: => A): Lifecycle[Identity, A] = {
    makeSimple(acquire) {
      es =>
        if (!(es.isShutdown || es.isTerminated)) {
          logger.info(s"Stopping $es...")
          es.shutdown()
          if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
            val dropped = es.shutdownNow()
            logger.warn(s"Executor $es didn't finish in time, ${dropped.size()} tasks were dropped")
          }
        }
    }
  }
}
