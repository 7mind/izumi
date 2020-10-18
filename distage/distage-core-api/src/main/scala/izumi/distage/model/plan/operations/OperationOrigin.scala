package izumi.distage.model.plan.operations

import izumi.distage.model.definition.Binding
import izumi.fundamentals.platform.language.SourceFilePosition

import scala.language.implicitConversions

sealed trait OperationOrigin {
  def toSynthetic: OperationOrigin.Synthetic

  @inline final def toSourceFilePosition: SourceFilePosition = fold(SourceFilePosition.unknown, _.origin)

  @inline final def fold[A](onUnknown: => A, onDefined: Binding => A): A = this match {
    case defined: OperationOrigin.Defined => onDefined(defined.binding)
    case OperationOrigin.Unknown => onUnknown
  }

  @inline final def foldPartial[A](onUnknown: => A, onDefined: PartialFunction[Binding, A]): A = this match {
    case defined: OperationOrigin.Defined => onDefined.applyOrElse(defined.binding, (_: Binding) => onUnknown)
    case OperationOrigin.Unknown => onUnknown
  }
}

object OperationOrigin {

  final case class EqualizedOperationOrigin(value: OperationOrigin) {
    override def hashCode(): Int = 0

    override def equals(obj: Any): Boolean = obj match {
      case _: EqualizedOperationOrigin => true
      case _: OperationOrigin => true
      case _ => false
    }
  }
  object EqualizedOperationOrigin {
    implicit def make(o: OperationOrigin): EqualizedOperationOrigin = EqualizedOperationOrigin(o)
  }

  sealed trait Defined extends OperationOrigin {
    def binding: Binding
    override def toSynthetic: Synthetic = SyntheticBinding(binding)
  }

  final case class UserBinding(binding: Binding) extends Defined

  sealed trait Synthetic extends OperationOrigin {
    override final def toSynthetic: Synthetic = this
  }

  final case class SyntheticBinding(binding: Binding) extends Defined with Synthetic
  final case object Unknown extends Synthetic
}
