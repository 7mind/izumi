package izumi.distage.model.reflection

import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameDispatcher
import izumi.reflect.Tag

final case class TypedRef[+T](private val v: T, tpe: SafeType, isByName: Boolean) extends GenericTypedRef[T] {
  def value: T = v match {
    case d: ByNameDispatcher => d.apply().asInstanceOf[T]
    case o => o
  }

  def asArgument(byName: Boolean): Any = {
    if (byName && !isByName) {
      () => v
    } else if (isByName && !byName) {
      v.asInstanceOf[Function0[Any]].apply()
    } else v
  }
}

object TypedRef {
  def apply[T: Tag](value: T): TypedRef[T] = TypedRef(value, SafeType.get[T], isByName = false)
  def byName[T: Tag](value: => T): TypedRef[T] = TypedRef((() => value).asInstanceOf[T], SafeType.get[T], isByName = true)
}
