package izumi.distage.model.reflection

import izumi.distage.model.reflection.IdContract.IdContractImpl

trait IdContract[T] {
  def repr(v: T): String
}

object IdContract extends LowPriorityIdContractInstances {
  def apply[T: IdContract]: IdContract[T] = implicitly

  implicit def singletonStringIdContract[S <: String & Singleton]: IdContract[S] = new IdContractImpl[S]

  final class IdContractImpl[T] extends IdContract[T] {
    override def repr(value: T): String = value.toString
  }
}

sealed trait LowPriorityIdContractInstances {
  implicit final val stringIdContract: IdContract[String] = new IdContractImpl[String]
}
