package izumi.distage.model.plan.operations

import izumi.distage.model.definition.Binding

sealed trait OperationOrigin {
  def toSynthetic: OperationOrigin.Synthetic
}

object OperationOrigin {
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
