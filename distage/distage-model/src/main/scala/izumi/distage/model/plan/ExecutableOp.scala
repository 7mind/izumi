package izumi.distage.model.plan

import izumi.distage.model.definition.Binding
import izumi.distage.model.plan.ExecutableOp.ProxyOp.MakeProxy
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait ExecutableOp {
  def target: DIKey

  def origin: Option[Binding]

  override def toString: String = new OpFormatter.Impl(KeyFormatter.Full, TypeFormatter.Full).format(this)
}

object ExecutableOp {

  sealed trait InstantiationOp extends ExecutableOp

  final case class ImportDependency(target: DIKey, references: Set[DIKey], origin: Option[Binding]) extends ExecutableOp

  final case class CreateSet(target: DIKey, tpe: SafeType, members: Set[DIKey], origin: Option[Binding]) extends InstantiationOp

  sealed trait WiringOp extends InstantiationOp {
    def target: DIKey

    def wiring: PureWiring

    def origin: Option[Binding]
  }

  object WiringOp {

    final case class InstantiateClass(target: DIKey, wiring: SingletonWiring.Constructor, origin: Option[Binding]) extends WiringOp

    final case class InstantiateTrait(target: DIKey, wiring: SingletonWiring.AbstractSymbol, origin: Option[Binding]) extends WiringOp

    final case class InstantiateFactory(target: DIKey, wiring: Factory, origin: Option[Binding]) extends WiringOp

    final case class CallProvider(target: DIKey, wiring: SingletonWiring.Function, origin: Option[Binding]) extends WiringOp

    final case class CallFactoryProvider(target: DIKey, wiring: FactoryFunction, origin: Option[Binding]) extends WiringOp

    final case class ReferenceInstance(target: DIKey, wiring: SingletonWiring.Instance, origin: Option[Binding]) extends WiringOp

    final case class ReferenceKey(target: DIKey, wiring: SingletonWiring.Reference, origin: Option[Binding]) extends WiringOp

  }

  sealed trait MonadicOp extends InstantiationOp {
    def effectWiring: PureWiring
    def wiring: Wiring.MonadicWiring
  }

  object MonadicOp {

    final case class ExecuteEffect(target: DIKey, effectOp: WiringOp, wiring: Wiring.MonadicWiring.Effect, origin: Option[Binding]) extends MonadicOp {
      override def effectWiring: RuntimeDIUniverse.Wiring.PureWiring = wiring.effectWiring
    }

    final case class AllocateResource(target: DIKey, effectOp: WiringOp, wiring: Wiring.MonadicWiring.Resource, origin: Option[Binding]) extends MonadicOp {
      override def effectWiring: RuntimeDIUniverse.Wiring.PureWiring = wiring.effectWiring
    }
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
          case u: Wiring.SingletonWiring =>
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



