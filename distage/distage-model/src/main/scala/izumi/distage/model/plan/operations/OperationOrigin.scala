package izumi.distage.model.plan.operations

import izumi.distage.model.definition.Binding

sealed trait OperationOrigin {
  def toSynthetic: OperationOrigin.Synthetic
}

object OperationOrigin {
  sealed trait Defined extends OperationOrigin {
    def binding: Binding
  }

  case class UserBinding(binding: Binding) extends Defined {
    override def toSynthetic: Synthetic = SyntheticBinding(binding)
  }

  sealed trait Synthetic extends OperationOrigin

  case class SyntheticBinding(binding: Binding) extends Synthetic with Defined {
    override def toSynthetic: Synthetic = this
  }

  case object Unknown extends Synthetic {
    override def toSynthetic: Synthetic = this
  }
}