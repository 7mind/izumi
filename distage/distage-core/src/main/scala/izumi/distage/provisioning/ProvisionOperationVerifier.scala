package izumi.distage.provisioning

import izumi.distage.commons.UnboxingTool
import izumi.distage.model.exceptions._
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.reflection
import izumi.distage.model.reflection.universe
import izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.reflection.TypeUtil


trait ProvisionOperationVerifier {
  def verify(target: RuntimeDIUniverse.DIKey, prohibited: scala.collection.Set[RuntimeDIUniverse.DIKey], value: Any, clue: String): Unit
}

object ProvisionOperationVerifier {

  object Null extends ProvisionOperationVerifier {
    override def verify(target: universe.RuntimeDIUniverse.DIKey, keys: collection.Set[universe.RuntimeDIUniverse.DIKey], value: Any, clue: String): Unit = {
      Quirks.discard(target, keys, value, clue)
    }
  }

  class Default(
                 mirror: MirrorProvider,
                 unboxingTool: UnboxingTool,
               ) extends ProvisionOperationVerifier {
    def verify(target: RuntimeDIUniverse.DIKey, keys: scala.collection.Set[RuntimeDIUniverse.DIKey], value: Any, clue: String): Unit = {
      if (keys.contains(target)) {
        throw DuplicateInstancesException(target)
      }

      val unboxed = unboxingTool.unbox(target, value)

      target match {
        case _: RuntimeDIUniverse.DIKey.ProxyElementKey => // each proxy operation returns two assignments
          throwIfIncompatible(RuntimeDIUniverse.DIKey.get[ProxyDispatcher], clue, unboxed)

        case _ =>
          unboxed match {
            case d: ByNameDispatcher =>
              val dispatcherTypeCompatible = d.key.tpe == target.tpe || (d.key.tpe <:< target.tpe)

              if (!dispatcherTypeCompatible) {
                throw new IncompatibleTypesException(s"Dispatcher contains incompatible key: ${target.tpe}, found: ${d.key.tpe}", target.tpe, d.key.tpe)
              }

            case u =>
              throwIfIncompatible(target, clue, u)
          }
      }


    }

    private def throwIfIncompatible(target: reflection.universe.RuntimeDIUniverse.DIKey, clue: String, o: AnyRef): Unit = {
      if (!runtimeClassCompatible(mirror, target, o)) {
        throw new IncompatibleRuntimeClassException(target, o.getClass, clue)
      }
    }
  }

  private def runtimeClassCompatible(mirror: MirrorProvider, target: RuntimeDIUniverse.DIKey, unboxed: AnyRef): Boolean = {
    mirror.runtimeClass(target.tpe) match {
      case Some(runtimeKeyClass) =>
        TypeUtil.isAssignableFrom(runtimeKeyClass, unboxed)

      case None =>
        true
    }
  }
}
