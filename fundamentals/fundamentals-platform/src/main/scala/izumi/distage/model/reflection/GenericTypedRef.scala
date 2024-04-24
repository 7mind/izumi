package izumi.distage.model.reflection

trait GenericTypedRef[+T] {
  def value: T
  def asArgument(byName: Boolean): Any
  def tpe: SafeType
}
