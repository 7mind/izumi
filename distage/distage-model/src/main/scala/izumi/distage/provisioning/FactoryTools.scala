package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.exceptions.UnexpectedProvisionResultException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.NewObjectOp
import com.github.pshirshov.izumi.distage.model.provisioning.NewObjectOp.{NewImport, NewInstance}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._

object FactoryTools {

  def interpret(results: Seq[NewObjectOp]): AnyRef = {
    results.toList match {
      case List(i: NewInstance) =>
        i.instance.asInstanceOf[AnyRef]
      case List(i: NewImport) =>
        i.instance.asInstanceOf[AnyRef]
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

  def mkExecutableOp(key: RuntimeDIUniverse.DIKey, wiring: RuntimeDIUniverse.Wiring.SingletonWiring, binding: Option[Binding]): WiringOp =
    wiring match {
      case w: SingletonWiring.Constructor =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.InstantiateClass(target, w, binding)

      case w: SingletonWiring.AbstractSymbol =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.InstantiateTrait(target, w, binding)

      case w: SingletonWiring.Function =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.CallProvider(target, w, binding)

      case w: SingletonWiring.Instance =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.ReferenceInstance(target, w, binding)

      case w: SingletonWiring.Reference =>
        val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
        WiringOp.ReferenceKey(target, w, binding)
    }

}
