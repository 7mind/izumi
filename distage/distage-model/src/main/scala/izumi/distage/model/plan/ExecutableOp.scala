package izumi.distage.model.plan

import izumi.distage.model.definition.Binding
import izumi.distage.model.plan.ExecutableOp.ProxyOp.MakeProxy
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.repr.{KeyFormatter, OpFormatter, TypeFormatter}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.annotation.tailrec




sealed trait ExecutableOp {
  def target: DIKey
  def origin: OperationOrigin

  override final def toString: String = new OpFormatter.Impl(KeyFormatter.Full, TypeFormatter.Full).format(this)
}

object ExecutableOp {

  sealed trait SemiplanOp extends ExecutableOp
  final case class ImportDependency(target: DIKey, references: Set[DIKey], origin: OperationOrigin.Synthetic) extends SemiplanOp

  sealed trait InstantiationOp extends SemiplanOp

  final case class CreateSet(target: DIKey, element: SafeType, members: Set[DIKey], origin: OperationOrigin) extends InstantiationOp

  sealed trait WiringOp extends InstantiationOp {
    def target: DIKey
    def wiring: PureWiring
    def origin: OperationOrigin
  }

  object WiringOp {
    final case class InstantiateClass(target: DIKey, wiring: SingletonWiring.Constructor, origin: OperationOrigin) extends WiringOp
    final case class InstantiateTrait(target: DIKey, wiring: SingletonWiring.AbstractSymbol, origin: OperationOrigin) extends WiringOp
    final case class InstantiateFactory(target: DIKey, wiring: Factory, origin: OperationOrigin) extends WiringOp
    final case class CallProvider(target: DIKey, wiring: SingletonWiring.Function, origin: OperationOrigin) extends WiringOp
    final case class CallFactoryProvider(target: DIKey, wiring: FactoryFunction, origin: OperationOrigin) extends WiringOp
    final case class ReferenceInstance(target: DIKey, wiring: SingletonWiring.Instance, origin: OperationOrigin) extends WiringOp
    final case class ReferenceKey(target: DIKey, wiring: SingletonWiring.Reference, origin: OperationOrigin) extends WiringOp
  }

  sealed trait MonadicOp extends InstantiationOp {
    def effectWiring: PureWiring
    def wiring: Wiring.MonadicWiring
  }

  object MonadicOp {
    final case class ExecuteEffect(target: DIKey, effectOp: WiringOp, wiring: Wiring.MonadicWiring.Effect, origin: OperationOrigin) extends MonadicOp {
      override def effectWiring: RuntimeDIUniverse.Wiring.PureWiring = wiring.effectWiring
    }
    final case class AllocateResource(target: DIKey, effectOp: WiringOp, wiring: Wiring.MonadicWiring.Resource, origin: OperationOrigin) extends MonadicOp {
      override def effectWiring: RuntimeDIUniverse.Wiring.PureWiring = wiring.effectWiring
    }
  }

  sealed trait ProxyOp extends ExecutableOp

  object ProxyOp {
    final case class MakeProxy(op: InstantiationOp, forwardRefs: Set[DIKey], origin: OperationOrigin, byNameAllowed: Boolean) extends ProxyOp {
      override def target: DIKey = op.target
    }
    final case class InitProxy(target: DIKey, dependencies: Set[DIKey], proxy: MakeProxy, origin: OperationOrigin) extends ProxyOp
  }

  implicit class ExecutableOpExt(val op: ExecutableOp) extends AnyVal {
    def instanceType: SafeType = opInstanceType(op)

  }

  @tailrec
  private[this] def opInstanceType(op: ExecutableOp): SafeType = {
    op match {
      case w: WiringOp =>
        w.wiring match {
          case u: Wiring.SingletonWiring =>
            u.instanceType
          case _: Wiring.Factory | _: Wiring.FactoryFunction =>
            w.target.tpe
        }
      case p: MakeProxy =>
        opInstanceType(p.op)
      case o =>
        o.target.tpe
    }
  }

//  @tailrec
//  def underlyingInstanceType(op: ExecutableOp): SafeType = {
//    op match {
//      case op: ImportDependency =>
//        op.target.tpe
//      case op: InstantiationOp =>
//        op match {
//          case op: CreateSet =>
//            op.target.tpe
//          case op: WiringOp =>
//            op.wiring match {
//              case u: Wiring.SingletonWiring =>
//                u.instanceType
//              case _: Wiring.Factory | _: Wiring.FactoryFunction =>
//                op.target.tpe
//            }
//          case op: MonadicOp =>
//            op match {
//              case eff: MonadicOp.ExecuteEffect =>
//                underlyingInstanceType(eff.effectOp)
//              case res: MonadicOp.AllocateResource =>
//                underlyingInstanceType(res.effectOp)
//            }
//        }
//      case op: ProxyOp =>
//        op match {
//          case p: MakeProxy =>
//            underlyingInstanceType(p.op)
//          case p: ProxyOp.InitProxy =>
//            underlyingInstanceType(p.proxy)
//        }
//    }
//  }

}



