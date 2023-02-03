package izumi.distage.model.reflection

trait IdContract[T] {
  def repr(v: T): String
}

object IdContract {
  def apply[T: IdContract]: IdContract[T] = implicitly

  implicit val stringIdContract: IdContract[String] = new IdContractImpl[String]

  implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S] = new IdContractImpl[S]

  final class IdContractImpl[T] extends IdContract[T] {
    override def repr(value: T): String = value.toString
  }
}
