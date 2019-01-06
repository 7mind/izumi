package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.plan
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.MakeProxy
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._


// TODO: typeclass?..
sealed trait ExecutableOp {
  def target: DIKey

  def origin: Option[Binding]

  override def toString: String = new plan.OpFormatter.Impl(KeyFormatter.Full, TypeFormatter.Full).format(this)
}


object ExecutableOp {

  sealed trait InstantiationOp extends ExecutableOp

  final case class ImportDependency(target: DIKey, references: Set[DIKey], origin: Option[Binding]) extends ExecutableOp

  final case class CreateSet(target: DIKey, tpe: SafeType, members: Set[DIKey], origin: Option[Binding]) extends InstantiationOp

  sealed trait WiringOp extends InstantiationOp {
    def target: DIKey

    def wiring: Wiring

    def origin: Option[Binding]
  }

  object WiringOp {

    final case class InstantiateClass(target: DIKey, wiring: UnaryWiring.Constructor, origin: Option[Binding]) extends WiringOp

    final case class InstantiateTrait(target: DIKey, wiring: UnaryWiring.AbstractSymbol, origin: Option[Binding]) extends WiringOp

    final case class InstantiateFactory(target: DIKey, wiring: FactoryMethod, origin: Option[Binding]) extends WiringOp

    final case class CallProvider(target: DIKey, wiring: UnaryWiring.Function, origin: Option[Binding]) extends WiringOp

    final case class CallFactoryProvider(target: DIKey, wiring: FactoryFunction, origin: Option[Binding]) extends WiringOp

    final case class ReferenceInstance(target: DIKey, wiring: UnaryWiring.Instance, origin: Option[Binding]) extends WiringOp

    final case class ReferenceKey(target: DIKey, wiring: UnaryWiring.Reference, origin: Option[Binding]) extends WiringOp

  }

  sealed trait ProxyOp extends ExecutableOp

  object ProxyOp {

    final case class MakeProxy(op: InstantiationOp, forwardRefs: Set[DIKey], origin: Option[Binding], byNameAllowed: Boolean) extends ProxyOp {
      override def target: DIKey = op.target
    }

    final case class InitProxy(target: DIKey, dependencies: Set[DIKey], proxy: MakeProxy, origin: Option[Binding]) extends ProxyOp

  }

  @scala.annotation.tailrec
  def instanceType(op: ExecutableOp): SafeType = {
    op match {
      case w: WiringOp =>
        w.wiring match {
          case u: Wiring.UnaryWiring =>
            u.instanceType
          case _ =>
            w.target.tpe
        }
      case p: MakeProxy =>
        instanceType(p.op)
      case o =>
        o.target.tpe
    }
  }
}



