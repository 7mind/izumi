package com.github.pshirshov.izumi.distage.roles.services

import java.util.concurrent.{ExecutorService, TimeUnit}

import com.github.pshirshov.izumi.distage.model.definition.DIResource.makeSimple
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class ResourceRewriter(logger: IzLogger) extends PlanningHook {
  override def hookDefinition(defn: ModuleBase): ModuleBase = {
    defn
      .flatMap(rewrite[AutoCloseable](a => fromAutoCloseable(a)))
      .flatMap(rewrite[ExecutorService](a => fromExecutorService(a)))
  }

  def fromAutoCloseable[A <: AutoCloseable](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire) {
      ac =>
      logger.debug(s"Closing $ac...")
      ac.close()
    }
  }

  def fromExecutorService[A <: ExecutorService](acquire: => A): DIResource[Identity, A] = {
    makeSimple(acquire) {
      es =>
        if (!(es.isShutdown || es.isTerminated)) {
          logger.debug(s"Stopping $es...")
          es.shutdown()
          if (!es.awaitTermination(1, TimeUnit.SECONDS)) {
            val dropped = es.shutdownNow()
            logger.warn(s"Executor $es didn't finish in time, ${dropped.size()} tasks were dropped")
          }
        }
    }
  }

  private def rewrite[T: Tag](convert: T => DIResource[Identity, T])(b: Binding): Seq[Binding] = {
    b match {
      case binding: Binding.ImplBinding =>
        binding match {
          case Binding.SingletonBinding(key, implementation, _tags, origin) =>
            rewriteImpl(convert, binding, key, implementation, _tags, origin)
          case Binding.SetElementBinding(key, implementation, _tags, origin) =>
            val rewritten = rewriteImpl(convert, binding, key, implementation, _tags, origin)
            rewritten.map {
              case b1 if b.key == key =>
                b1 match {
                  case binding: Binding.ImplBinding =>
                    Binding.SetElementBinding(key, binding.implementation, _tags, origin)
                  case binding: Binding.SetBinding =>
                    binding match {
                      case s: Binding.SetElementBinding[_] =>
                        Binding.SetElementBinding(key, s.implementation, _tags, origin)
                      case esb: Binding.EmptySetBinding[_] =>
                        ???
                    }

                }

              case o => o
            }

        }
      case binding: Binding.SetBinding =>
        Seq(binding)

    }
  }

  private def rewriteImpl[T: Tag](convert: T => DIResource[Identity, T], binding: Binding.ImplBinding, key: RuntimeDIUniverse.DIKey, implementation: ImplDef, _tags: Set[BindingTag], origin: SourceFilePosition) = {
    implementation match {
      case implDef: ImplDef.DirectImplDef =>
        if (implDef.implType weak_<:< SafeType.get[T]) {
          val resourceType = SafeType.get[DIResource[Identity, Any]]

          implDef match {
            case _: ImplDef.ReferenceImpl =>
              Seq(binding)
            case ImplDef.InstanceImpl(_, instance) =>
              val newImpl = ImplDef.InstanceImpl(resourceType, convert(instance.asInstanceOf[T]))
              finish(key, _tags, origin, implDef, newImpl)
            case ImplDef.ProviderImpl(_, function) =>
              val newImpl = function.unsafeMap(resourceType, (instance: Any) => convert(instance.asInstanceOf[T]))
              finish(key, _tags, origin, implDef, ImplDef.ProviderImpl(resourceType, newImpl))
            case ImplDef.TypeImpl(_) =>
              val tpe = key.tpe
              val newkey = DIKey.IdKey(key.tpe, ResourceRewriter.ResId(key))

              val debugInfo = DependencyContext.ConstructorParameterContext(tpe, SymbolInfo.Static("x$1", tpe, Nil, tpe, isByName = false, wasGeneric = false))

              val p = Provider.ProviderImpl(
                associations = Seq(Association.Parameter(debugInfo, "x$1", tpe, newkey, isByName = false, wasGeneric = false))
                , fun = (s: Seq[Any]) => convert(s.head.asInstanceOf[T])
                , ret = tpe
              )

              Seq(
                Binding.SingletonBinding(newkey, implementation, _tags, origin)
              ) ++ finish(key, _tags, origin, implDef, ImplDef.ProviderImpl(resourceType, p))
          }


        } else {
          Seq(binding)
        }
      case _: ImplDef.RecursiveImplDef =>
        Seq(binding)
    }
  }

  private def finish(key: DIKey, _tags: Set[BindingTag], origin: SourceFilePosition, implDef: ImplDef.DirectImplDef, newImpl: ImplDef.DirectImplDef): Seq[Binding] = {
    val res = ImplDef.ResourceImpl(implDef.implType, SafeType.getK[Identity], newImpl)
    Seq(Binding.SingletonBinding(key, res, _tags, origin))
  }
}

object ResourceRewriter {
  final case class ResId(contextKey: DIKey) {
    override def toString: String = s"res:${contextKey.toString}"
  }

}
