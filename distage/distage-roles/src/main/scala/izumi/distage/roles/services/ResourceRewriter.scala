package izumi.distage.roles.services

import java.util.concurrent.{ExecutorService, TimeUnit}

import izumi.distage.model.definition.Binding.{SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.DIResource.makeSimple
import izumi.distage.model.definition.ImplDef.DirectImplDef
import izumi.distage.model.definition._
import izumi.distage.model.planning.PlanningHook
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.roles.services.ResourceRewriter.RewriteRules
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.logstage.api.IzLogger

class ResourceRewriter(
                        logger: IzLogger,
                        rules: RewriteRules,
                      ) extends PlanningHook {

  import ResourceRewriter._
  import RewriteResult._

  override def hookDefinition(defn: ModuleBase): ModuleBase = {
    if (rules.applyRewrites) {
      defn
        .flatMap(rewrite[AutoCloseable](fromAutoCloseable(logger, _)))
        .flatMap(rewrite[ExecutorService](fromExecutorService(logger, _)))
    } else {
      defn
    }
  }

  private def rewrite[TGT: Tag](convert: TGT => DIResource[Identity, TGT])(b: Binding): Seq[Binding] = {
    b match {
      case implBinding: Binding.ImplBinding =>
        implBinding match {
          case binding: Binding.SingletonBinding[_] =>
            rewriteImpl(convert, binding.key, binding.origin, binding.implementation, set = false) match {
              case ReplaceImpl(newImpl) =>
                logger.info(s"Adapting ${binding.key} defined at ${binding.origin} as ${SafeType.get[TGT] -> "type"}")
                Seq(finish(binding, newImpl))
              case ReplaceImplMoveOrigToResourceKey(newImpl, resourceKey) =>
                logger.info(s"Adapting ${binding.key} defined at ${binding.origin} as ${SafeType.get[TGT] -> "type"}, $resourceKey")
                Seq(binding.copy(key = resourceKey), finish(binding, newImpl))
              case DontChange =>
                Seq(binding)
            }

          case binding: Binding.SetElementBinding =>
            rewriteImpl(convert, binding.key, binding.origin, binding.implementation, set = true) match {
              case ReplaceImpl(newImpl) =>
                logger.info(s"Adapting set element ${binding.key} defined at ${binding.origin} as ${SafeType.get[TGT] -> "type"}")
                Seq(finish(binding, newImpl))
              case ReplaceImplMoveOrigToResourceKey(newImpl, resourceKey) =>
                logger.info(s"Adapting set element ${binding.key} defined at ${binding.origin} as ${SafeType.get[TGT] -> "type"}, $resourceKey")
                val elementResourceBinding = SingletonBinding(resourceKey, binding.implementation, binding.tags, binding.origin)
                Seq(elementResourceBinding, finish(binding, newImpl))
              case RewriteResult.DontChange =>
                Seq(binding)
            }
        }

      case binding: Binding.SetBinding =>
        Seq(binding)
    }
  }

  private def rewriteImpl[TGT: Tag](convert: TGT => DIResource[Identity, TGT], key: DIKey, origin: SourceFilePosition, implementation: ImplDef, set: Boolean): RewriteResult = {
    implementation match {
      case implDef: ImplDef.DirectImplDef =>
        val implType = implDef.implType
        if (implType <:< SafeType.get[TGT]) {
          val resourceType = SafeType.get[DIResource[Identity, TGT]]

          implDef match {
            case _: ImplDef.ReferenceImpl =>
              DontChange

            case _: ImplDef.InstanceImpl =>
              if (rules.warnOnExternal) {
                logger.warn(s"External entity $key defined at $origin is <:< ${SafeType.get[TGT] -> "type"}, but it will NOT be finalized!!! Because it's not an explicit DIResource")
              }
              DontChange

            case ImplDef.ProviderImpl(_, function) =>
              val newImpl = function.unsafeMap(resourceType, (instance: Any) => convert(instance.asInstanceOf[TGT]))
              ReplaceImpl(ImplDef.ProviderImpl(resourceType, newImpl))

            case ImplDef.TypeImpl(_) =>
              val implTypeKey = DIKey.TypeKey(implType)
              val newKey = DIKey.IdKey(
                tpe = implType,
                id = ResId(if (set) DIKey.SetElementKey(key, implTypeKey) else implTypeKey)
              )

              val parameter = {
                val symbolInfo = SymbolInfo.Static(name = "x$1", finalResultType = implType, annotations = Nil, definingClass = implType, isByName = false, wasGeneric = false)
                val debugInfo = DependencyContext.ConstructorParameterContext(implType, symbolInfo)
                Association.Parameter(context = debugInfo, name = "x$1", tpe = implType, wireWith = newKey, isByName = false, wasGeneric = false)
              }

              val fn = Provider.ProviderImpl(
                associations = Seq(parameter),
                fun = {
                  s: Seq[Any] =>
                    convert(s.head.asInstanceOf[TGT])
                },
                ret = resourceType
              )

              ReplaceImplMoveOrigToResourceKey(ImplDef.ProviderImpl(resourceType, fn), newKey)
          }
        } else {
          DontChange
        }
      case implDef: ImplDef.RecursiveImplDef =>
        implDef match {
          case _: ImplDef.EffectImpl =>
            if (implDef.implType <:< SafeType.get[TGT]) {
              logger.error(s"Effect entity $key defined at $origin is ${SafeType.get[TGT] -> "type"}, but it will NOT be finalized!!! You must explicitly wrap it into resource using DIResource.fromAutoCloseable/fromExecutorService")
            }
            DontChange

          case _: ImplDef.ResourceImpl =>
            DontChange
        }
    }
  }

  private def finish(original: SingletonBinding[DIKey], newImpl: DirectImplDef): Binding = {
    val res = ImplDef.ResourceImpl(original.implementation.implType, SafeType.getK[Identity], newImpl)
    original.copy(implementation = res)
  }

  private def finish(original: SetElementBinding, newImpl: DirectImplDef): Binding = {
    val res = ImplDef.ResourceImpl(original.implementation.implType, SafeType.getK[Identity], newImpl)
    original.copy(implementation = res)
  }

}

object ResourceRewriter {

  sealed trait RewriteResult
  object RewriteResult {
    final case class ReplaceImpl(newImpl: DirectImplDef) extends RewriteResult
    final case class ReplaceImplMoveOrigToResourceKey(newImpl: DirectImplDef, newKey: DIKey) extends RewriteResult
    case object DontChange extends RewriteResult
  }

  final case class ResId(disambiguator: DIKey) {
    override def toString: String = s"res:${disambiguator.toString}"
  }

  object ResId {
    implicit val idContract: IdContract[ResId] = new RuntimeDIUniverse.IdContractImpl[ResId]
  }

  final case class RewriteRules(
                                 applyRewrites: Boolean = true,
                                 warnOnExternal: Boolean = true,
                               )

  /** Like [[DIResource.fromAutoCloseable]], but with added logging */
  def fromAutoCloseable[A <: AutoCloseable](logger: IzLogger, acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire) {
      ac =>
        logger.info(s"Closing $ac...")
        ac.close()
    }
  }

  /** Like [[DIResource.fromExecutorService]], but with added logging */
  def fromExecutorService[A <: ExecutorService](logger: IzLogger, acquire: => A): DIResource[Identity, A] = {
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
