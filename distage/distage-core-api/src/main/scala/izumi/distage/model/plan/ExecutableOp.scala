package izumi.distage.model.plan

import izumi.distage.model.exceptions.DIBugException
import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.Wiring.SingletonWiring
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.plan.repr.{KeyFormatter, OpFormatter, TypeFormatter}
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.reflect.TagK

import scala.annotation.tailrec

sealed trait ExecutableOp {
  def target: DIKey
  def origin: EqualizedOperationOrigin
  def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): ExecutableOp
  override final def toString: String = new OpFormatter.Impl(KeyFormatter.Full, TypeFormatter.Full).format(this)
}

object ExecutableOp {

  sealed trait SemiplanOp extends ExecutableOp
  final case class ImportDependency(target: DIKey, references: Set[DIKey], origin: EqualizedOperationOrigin) extends SemiplanOp {
    override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): ImportDependency = {
      this.copy(target = targets(this.target))
    }
  }

  sealed trait NonImportOp extends ExecutableOp
  sealed trait InstantiationOp extends SemiplanOp with NonImportOp {
    def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): InstantiationOp
  }
  final case class CreateSet(target: DIKey, members: Set[DIKey], origin: EqualizedOperationOrigin) extends InstantiationOp {
    override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): CreateSet = {
      this.copy(target = targets(this.target), members = this.members.map(parameters))
    }
  }

  sealed trait WiringOp extends InstantiationOp {
    def target: DIKey
    def wiring: SingletonWiring
    def origin: EqualizedOperationOrigin
  }
  object WiringOp {
    final case class CallProvider(target: DIKey, wiring: SingletonWiring.Function, origin: EqualizedOperationOrigin /*, isMutator: Boolean*/ ) extends WiringOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): CallProvider = {
        this.copy(target = targets(this.target), wiring = this.wiring.replaceKeys(parameters))
      }
    }
    final case class UseInstance(target: DIKey, wiring: SingletonWiring.Instance, origin: EqualizedOperationOrigin) extends WiringOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): UseInstance = {
        this.copy(target = targets(this.target), wiring = this.wiring.replaceKeys(parameters))
      }
    }
    final case class ReferenceKey(target: DIKey, wiring: SingletonWiring.Reference, origin: EqualizedOperationOrigin) extends WiringOp {
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
    final case class ExecuteEffect(target: DIKey, effectKey: DIKey, instanceTpe: SafeType, effectHKTypeCtor: SafeType, origin: EqualizedOperationOrigin)
      extends MonadicOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): ExecuteEffect = {
        this.copy(target = targets(this.target), effectKey = parameters(this.effectKey))
      }
    }
    final case class AllocateResource(target: DIKey, effectKey: DIKey, instanceTpe: SafeType, effectHKTypeCtor: SafeType, origin: EqualizedOperationOrigin)
      extends MonadicOp {
      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): AllocateResource = {
        this.copy(target = targets(this.target), effectKey = parameters(this.effectKey))
      }
    }

    implicit final class MonadicOpExt(private val op: MonadicOp) extends AnyVal {
      @inline def actionEffectType: SafeType = op.effectHKTypeCtor
      @inline def provisionerEffectType[F[_]: TagK]: SafeType = SafeType.getK[F]

      @inline def isEffect[F[_]: TagK]: Boolean = {
        actionEffectType != SafeType.identityEffectType
      }
      @inline def isIncompatibleEffectType[F[_]: TagK]: Boolean = {
        isEffect && !(actionEffectType <:< provisionerEffectType[F])
      }

      @inline def throwOnIncompatibleEffectType[F[_]: TagK](): Unit = {
        if (isIncompatibleEffectType[F]) {
          throw DIBugException(
            s"Incompatible effect type in operation ${op.target}: $actionEffectType !<:< ${SafeType.identityEffectType}; this had to be handled before"
          )
        }
      }
    }
  }

  sealed trait ProxyOp extends NonImportOp
  object ProxyOp {
    final case class MakeProxy(op: InstantiationOp, forwardRefs: Set[DIKey], origin: EqualizedOperationOrigin, byNameAllowed: Boolean) extends ProxyOp {
      override def target: DIKey = op.target

      override def replaceKeys(targets: DIKey => DIKey, parameters: DIKey => DIKey): MakeProxy = {
        this.copy(op = this.op.replaceKeys(targets, parameters), forwardRefs.map(parameters))
      }
    }
    final case class InitProxy(target: DIKey, dependencies: Set[DIKey], proxy: MakeProxy, origin: EqualizedOperationOrigin) extends ProxyOp {
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
