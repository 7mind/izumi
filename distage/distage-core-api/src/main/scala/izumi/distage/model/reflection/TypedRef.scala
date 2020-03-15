package izumi.distage.model.reflection

import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameDispatcher
import izumi.fundamentals.reflection.Tags.Tag

final case class TypedRef[+T](private val v: T, tpe: SafeType, isByName: Boolean) {
  def value: T = v match {
    case d: ByNameDispatcher => d.apply().asInstanceOf[T]
    case o => o
  }
}
object TypedRef {
  def apply[T: Tag](value: T): TypedRef[T] = TypedRef(value, SafeType.get[T], isByName = false)
  def byName[T: Tag](value: => T): TypedRef[T] = TypedRef((() => value).asInstanceOf[T], SafeType.get[T], isByName = true)
}
