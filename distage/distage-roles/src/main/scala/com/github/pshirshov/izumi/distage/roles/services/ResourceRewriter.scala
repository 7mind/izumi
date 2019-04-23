package com.github.pshirshov.izumi.distage.roles.services

import java.util.concurrent.{ExecutorService, TimeUnit}

import com.github.pshirshov.izumi.distage.model.definition.Binding.{SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.DIResource.makeSimple
import com.github.pshirshov.izumi.distage.model.definition.ImplDef.DirectImplDef
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.IzLogger

class ResourceRewriter(logger: IzLogger) extends PlanningHook {

  import ResourceRewriter._
  import RewriteResult._

  override def hookDefinition(defn: ModuleBase): ModuleBase = {
    defn
      .flatMap(rewrite[AutoCloseable](a => fromAutoCloseable(a)))
      .flatMap(rewrite[ExecutorService](a => fromExecutorService(a)))
  }


  private def rewrite[T: Tag](convert: T => DIResource[Identity, T])(b: Binding): Seq[Binding] = {
    b match {
      case binding: Binding.ImplBinding =>
        binding match {
          case b: Binding.SingletonBinding[_] =>
            rewriteImpl(convert, b.key, b.implementation) match {
              case ReplaceImpl(newImpl) =>
                logger.info(s"Adapting ${b.key} defined at ${b.origin} as ${implicitly[Tag[T]].tag -> "type"}")
                Seq(finish(b, newImpl))
              case ReplaceAndPreserve(newImpl, originalKey) =>
                logger.info(s"Adapting ${b.key} defined at ${b.origin} as ${implicitly[Tag[T]].tag -> "type"}")
                Seq(b.copy(key = originalKey), finish(b, newImpl))
              case DontChange =>
                Seq(binding)
            }


          case b: Binding.SetElementBinding[_] =>
            rewriteImpl(convert, b.key, b.implementation) match {
              case ReplaceImpl(newImpl) =>
                logger.info(s"Adapting ${b.key} defined at ${b.origin} as ${implicitly[Tag[T]].tag -> "type"}")
                Seq(finish(b, newImpl))
              case ReplaceAndPreserve(newImpl, originalKey) =>
                logger.info(s"Adapting ${b.key} defined at ${b.origin} as ${implicitly[Tag[T]].tag -> "type"}")
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


  private def rewriteImpl[T: Tag](convert: T => DIResource[Identity, T], key: DIKey, implementation: ImplDef): RewriteResult = {
    import RewriteResult._
    implementation match {
      case implDef: ImplDef.DirectImplDef =>
        if (implDef.implType weak_<:< SafeType.get[T]) {
          val resourceType = SafeType.get[DIResource[Identity, Any]]

          implDef match {
            case _: ImplDef.ReferenceImpl =>
              DontChange

            case ImplDef.InstanceImpl(_, instance) =>
              val newImpl = ImplDef.InstanceImpl(resourceType, convert(instance.asInstanceOf[T]))
              ReplaceImpl(newImpl)

            case ImplDef.ProviderImpl(_, function) =>
              val newImpl = function.unsafeMap(resourceType, (instance: Any) => convert(instance.asInstanceOf[T]))
              ReplaceImpl(ImplDef.ProviderImpl(resourceType, newImpl))

            case ImplDef.TypeImpl(_) =>
              val tpe = key.tpe
              val newkey = DIKey.IdKey(key.tpe, ResourceRewriter.ResId(key))

              val debugInfo = DependencyContext.ConstructorParameterContext(tpe, SymbolInfo.Static("x$1", tpe, Nil, tpe, isByName = false, wasGeneric = false))

              val p = Provider.ProviderImpl(
                associations = Seq(Association.Parameter(debugInfo, "x$1", tpe, newkey, isByName = false, wasGeneric = false))
                , fun = (s: Seq[Any]) => convert(s.head.asInstanceOf[T])
                , ret = tpe
              )

              ReplaceAndPreserve(ImplDef.ProviderImpl(resourceType, p), newkey)
          }
        } else {
          DontChange
        }
      case _: ImplDef.RecursiveImplDef =>
        DontChange
    }
  }


  private def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire) {
      ac =>
        logger.info(s"Closing $ac...")
        ac.close()
    }
  }

  private def fromExecutorService[A <: ExecutorService](acquire: => A): DIResource[Identity, A] = {
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

object ResourceRewriter {

  sealed trait RewriteResult

  object RewriteResult {

    case class ReplaceImpl(newImpl: DirectImplDef) extends RewriteResult

    case class ReplaceAndPreserve(newImpl: DirectImplDef, originalKey: DIKey) extends RewriteResult

    case object DontChange extends RewriteResult

  }

  final case class ResId(contextKey: DIKey) {
    override def toString: String = s"res:${contextKey.toString}"
  }

}
