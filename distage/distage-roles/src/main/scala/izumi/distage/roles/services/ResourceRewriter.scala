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
import izumi.fundamentals.platform.jvm.SourceFilePosition
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
        .flatMap(rewrite[AutoCloseable](a => fromAutoCloseable(logger, a)))
        .flatMap(rewrite[ExecutorService](a => fromExecutorService(logger, a)))
    } else {
      defn
    }
  }

  private def rewrite[T: Tag](convert: T => DIResource[Identity, T])(b: Binding): Seq[Binding] = {
    b match {
      case binding: Binding.ImplBinding =>
        binding match {
          case b: Binding.SingletonBinding[_] =>
            rewriteImpl(convert, b.key, b.origin, b.implementation, set = false) match {
              case ReplaceImpl(newImpl) =>
                logger.info(s"Adapting ${b.key} defined at ${b.origin} as ${SafeType.get[T] -> "type"}")
                Seq(finish(b, newImpl))
              case ReplaceAndPreserve(newImpl, originalKey) =>
                logger.info(s"Adapting ${b.key} defined at ${b.origin} as ${SafeType.get[T] -> "type"}, ${originalKey -> "preserved"}")
                Seq(b.copy(key = originalKey), finish(b, newImpl))
              case DontChange =>
                Seq(binding)
            }


          case b: Binding.SetElementBinding[_] =>
            rewriteImpl(convert, b.key, b.origin, b.implementation, set = true) match {
              case ReplaceImpl(newImpl) =>
                logger.info(s"Adapting set element ${b.key} defined at ${b.origin} as ${SafeType.get[T] -> "type"}")
                Seq(finish(b, newImpl))
              case ReplaceAndPreserve(newImpl, originalKey) =>
                logger.info(s"Adapting set element ${b.key} defined at ${b.origin} as ${SafeType.get[T] -> "type"}, ${originalKey -> "preserved"}")
                Seq(b.copy(key = originalKey), finish(b, newImpl))
              case RewriteResult.DontChange =>
                Seq(binding)
            }
        }

      case binding: Binding.SetBinding =>
        Seq(binding)
    }
  }

  private def finish(original: SingletonBinding[DIKey], newImpl: DirectImplDef): Binding = {
    val res = ImplDef.ResourceImpl(original.implementation.implType, SafeType.getK[Identity], newImpl)
    original.copy(implementation = res)
  }

  private def finish(original: SetElementBinding[DIKey], newImpl: DirectImplDef): Binding = {
    val res = ImplDef.ResourceImpl(original.implementation.implType, SafeType.getK[Identity], newImpl)
    original.copy(implementation = res)
  }

  private def rewriteImpl[T: Tag](convert: T => DIResource[Identity, T], key: DIKey, origin: SourceFilePosition, implementation: ImplDef, set: Boolean): RewriteResult = {
    import RewriteResult._

    implementation match {
      case implDef: ImplDef.DirectImplDef =>
        if (implDef.implType <:< SafeType.get[T]) {
          val resourceType = SafeType.get[DIResource[Identity, Any]]

          implDef match {
            case _: ImplDef.ReferenceImpl =>
              DontChange

            case _: ImplDef.InstanceImpl =>
              if (rules.warnOnExternal) {
                logger.warn(s"External entity $key defined at $origin is ${SafeType.get[T] -> "type"}, it will NOT be finalized!")
              }
              DontChange

            case ImplDef.ProviderImpl(_, function) =>
              val newImpl = function.unsafeMap(resourceType, (instance: Any) => convert(instance.asInstanceOf[T]))
              ReplaceImpl(ImplDef.ProviderImpl(resourceType, newImpl))

            case ImplDef.TypeImpl(_) =>
              val tpe = key.tpe
              val newkey = DIKey.IdKey(key.tpe, ResourceRewriter.ResId(key))

              val debugInfo = DependencyContext.ConstructorParameterContext(tpe, SymbolInfo.Static("x$1", tpe, Nil, tpe, isByName = false, wasGeneric = false))

              val p = Provider.ProviderImpl(
                associations = Seq(Association.Parameter(debugInfo, "x$1", tpe, newkey, isByName = false, wasGeneric = false))
                , fun = {
                  s: Seq[Any] =>
                    println(("!!!", key, newkey, implDef, implDef.implType, SafeType.get[T], s.head, s))
                    convert(s.head.asInstanceOf[T])
                }
                , ret = tpe
              )

              ReplaceAndPreserve(ImplDef.ProviderImpl(resourceType, p), newkey)
          }
        } else {
          DontChange
        }
      case implDef: ImplDef.RecursiveImplDef =>
        implDef match {
          case _: ImplDef.EffectImpl =>
            if (implDef.implType <:< SafeType.get[T]) {
              logger.error(s"Effect entity $key defined at $origin is ${SafeType.get[T] -> "type"} and it will NOT be finalized! You must wrap it into resource using DIResource.make")
            }
            DontChange

          case _: ImplDef.ResourceImpl =>
            DontChange

        }

    }
  }

}

object ResourceRewriter {

  sealed trait RewriteResult

  object RewriteResult {

    final case class ReplaceImpl(newImpl: DirectImplDef) extends RewriteResult

    final case class ReplaceAndPreserve(newImpl: DirectImplDef, originalKey: DIKey) extends RewriteResult

    case object DontChange extends RewriteResult

  }

  final case class ResId(contextKey: DIKey) {
    override def toString: String = s"res:${contextKey.toString}"
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
