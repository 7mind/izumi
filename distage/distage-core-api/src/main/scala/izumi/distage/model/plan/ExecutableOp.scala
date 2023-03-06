package izumi.distage.model.plan

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.definition.errors.ProvisionerIssue.IncompatibleEffectType
import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.Wiring.SingletonWiring
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.plan.repr.{DIRendering, KeyFormatter, OpFormatter, TypeFormatter}
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.DIKey.ProxyInitKey
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.fundamentals.platform.cache.CachedProductHashcode
import izumi.reflect.TagK

import scala.annotation.tailrec

sealed abstract class ExecutableOp extends Product with CachedProductHashcode {
  def target: DIKey
  def origin: EqualizedOperationOrigin
  override final def toString: String = {
    OpFormatter(KeyFormatter.Full, TypeFormatter.Full, DIRendering.colorsEnabled).format(this)
  }
}

object ExecutableOp {

  sealed trait SemiplanOp extends ExecutableOp
  sealed trait ImportOp extends SemiplanOp

  final case class ImportDependency(target: DIKey, references: Set[DIKey], origin: EqualizedOperationOrigin) extends ImportOp

  final case class AddRecursiveLocatorRef(references: Set[DIKey], origin: EqualizedOperationOrigin) extends ImportOp {
    override def target: DIKey = AddRecursiveLocatorRef.magicLocatorKey
  }

  object AddRecursiveLocatorRef {
    final val magicLocatorKey = DIKey.get[LocatorRef]
  }

  def createImport(target: DIKey, references: Set[DIKey], origin: EqualizedOperationOrigin): ImportOp = {
    if (target == AddRecursiveLocatorRef.magicLocatorKey) {
      AddRecursiveLocatorRef(references, origin)
    } else {
      ImportDependency(target, references, origin)
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

      @inline def throwOnIncompatibleEffectType[F[_]: TagK](): Either[ProvisionerIssue, Unit] = {
        if (isIncompatibleEffectType[F]) {
          Left(IncompatibleEffectType(op.target, actionEffectType))
        } else {
          Right(())
        }
      }
    }
  }

  sealed trait ProxyOp extends NonImportOp
  object ProxyOp {
    final case class MakeProxy(op: InstantiationOp, forwardRefs: Set[DIKey], origin: EqualizedOperationOrigin, byNameAllowed: Boolean) extends ProxyOp {
      override def target: DIKey = op.target
    }

    final case class InitProxy(target: ProxyInitKey, dependencies: Set[DIKey], proxy: MakeProxy, origin: EqualizedOperationOrigin) extends ProxyOp
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
      case i @ (_: InitProxy | _: ImportOp | _: CreateSet) =>
        i.target.tpe
    }
  }

}
