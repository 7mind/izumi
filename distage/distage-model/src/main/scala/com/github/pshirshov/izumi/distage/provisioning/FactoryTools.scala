package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.exceptions.UnexpectedProvisionResultException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.OpResult
import com.github.pshirshov.izumi.distage.model.provisioning.OpResult.{NewImport, NewInstance}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._

object FactoryTools {

  def interpret(results: Seq[OpResult]): AnyRef = {
    results.toList match {
      case List(i: NewInstance) =>
        i.value.asInstanceOf[AnyRef]
      case List(i: NewImport) =>
        i.value.asInstanceOf[AnyRef]
      case List(_) =>
        throw new UnexpectedProvisionResultException(
          s"Factory returned a result class other than NewInstance or NewImport in $results", results)
      case _ :: _ =>
        throw new UnexpectedProvisionResultException(
          s"Factory returned more than one result in $results", results)
      case Nil =>
        throw new UnexpectedProvisionResultException(
          s"Factory empty result list: $results", results)
    }
  }

  def mkExecutableOp(key: RuntimeDIUniverse.DIKey, wiring: RuntimeDIUniverse.Wiring.UnaryWiring): WiringOp =
    wiring match {
      case w: UnaryWiring.Constructor =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.InstantiateClass(target, w)

      case w: UnaryWiring.AbstractSymbol =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.InstantiateTrait(target, w)

      case w: UnaryWiring.Function =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.CallProvider(target, w)

      case w: UnaryWiring.Instance =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.ReferenceInstance(target, w)

      case w: UnaryWiring.Reference =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.ReferenceKey(target, w)
    }

}
