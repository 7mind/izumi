package izumi.distage.model.plan

import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.Wiring.SingletonWiring
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.repr.{KeyFormatter, OpFormatter, TypeFormatter}
import izumi.distage.model.reflection.{DIKey, SafeType}

import scala.annotation.tailrec

sealed trait ExecutableOp {
  def target: DIKey
  def origin: OperationOrigin
  def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): ExecutableOp
  override final def toString: String = new OpFormatter.Impl(KeyFormatter.Full, TypeFormatter.Full).format(this)
}

object ExecutableOp {

  sealed trait SemiplanOp extends ExecutableOp
  final case class ImportDependency(target: DIKey, references: Set[DIKey], origin: OperationOrigin.Synthetic) extends SemiplanOp {
    override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): ImportDependency = {
      this.copy(target = targets(this.target))
    }
  }

  sealed trait InstantiationOp extends SemiplanOp {
    def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): InstantiationOp
  }
  final case class CreateSet(target: DIKey, element: SafeType, members: Set[DIKey], origin: OperationOrigin) extends InstantiationOp {
    override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): CreateSet = {
      this.copy(target = targets(this.target), members = this.members.map(parameters))
    }
  }

  sealed trait WiringOp extends InstantiationOp {
    def target: DIKey
    def wiring: SingletonWiring
    def origin: OperationOrigin
  }
  object WiringOp {
    final case class CallProvider(target: DIKey, wiring: SingletonWiring.Function, origin: OperationOrigin) extends WiringOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): CallProvider = {
        this.copy(target = targets(this.target), wiring = this.wiring.replaceKeys(parameters))
      }
    }
    final case class UseInstance(target: DIKey, wiring: SingletonWiring.Instance, origin: OperationOrigin) extends WiringOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): UseInstance = {
        this.copy(target = targets(this.target), wiring = this.wiring.replaceKeys(parameters))
      }
    }
    final case class ReferenceKey(target: DIKey, wiring: SingletonWiring.Reference, origin: OperationOrigin) extends WiringOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): ReferenceKey = {
        this.copy(target = targets(this.target), wiring = this.wiring.replaceKeys(parameters))
      }
    }
  }

  sealed trait MonadicOp extends InstantiationOp {
    def effectKey: DIKey
    private[ExecutableOp] def instanceTpe: SafeType
    def effectHKTypeCtor: SafeType
  }
  object MonadicOp {
    final case class ExecuteEffect(target: DIKey, effectKey: DIKey, instanceTpe: SafeType, effectHKTypeCtor: SafeType, origin: OperationOrigin) extends MonadicOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): ExecuteEffect = {
        this.copy(target = targets(this.target), effectKey = parameters(this.effectKey))
      }
    }
    final case class AllocateResource(target: DIKey, effectKey: DIKey, instanceTpe: SafeType, effectHKTypeCtor: SafeType, origin: OperationOrigin) extends MonadicOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): AllocateResource = {
        this.copy(target = targets(this.target), effectKey = parameters(this.effectKey))
      }
    }
  }

  sealed trait ProxyOp extends ExecutableOp
  object ProxyOp {
    final case class MakeProxy(op: InstantiationOp, forwardRefs: Set[DIKey], origin: OperationOrigin, byNameAllowed: Boolean) extends ProxyOp {
      override def target: DIKey = op.target

      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): MakeProxy = {
        this.copy(op = this.op.replaceKeys(targets, parameters), forwardRefs.map(parameters))
      }
    }
    final case class InitProxy(target: DIKey, dependencies: Set[DIKey], proxy: MakeProxy, origin: OperationOrigin) extends ProxyOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): InitProxy = {
        this.copy(target = targets(this.target), dependencies = this.dependencies.map(parameters), proxy = this.proxy.replaceKeys(targets, parameters))
      }
    }
  }

  implicit final class ExecutableOpExt(private val op: ExecutableOp) extends AnyVal {
    @inline def instanceType: SafeType = opInstanceType(op)
  }

  @tailrec
  private[this] def opInstanceType(op: ExecutableOp): SafeType = {
    op match {
      case w: WiringOp =>
        w.wiring.instanceType
      case m: MonadicOp =>
        m.instanceTpe
      case p: MakeProxy =>
        opInstanceType(p.op)
      case i @ (_: InitProxy | _: ImportDependency | _: CreateSet) =>
        i.target.tpe
    }
  }

}
