package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.commons.UnboxingTool
import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.reflection
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.reflection.TypeUtil


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
        case RuntimeDIUniverse.DIKey.ProxyElementKey(_, _) => // each proxy operation returns two assignments
          throwIfIncompatible(RuntimeDIUniverse.DIKey.get[ProxyDispatcher], clue, unboxed)

        case _ =>
          unboxed match {
            case d: ByNameDispatcher =>
              val dispatcherTypeCompatible = d.key.tpe == target.tpe || (d.key.tpe weak_<:< target.tpe)

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
