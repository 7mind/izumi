package izumi.distage.model.references

import izumi.distage.model.provisioning.strategies.ProxyDispatcher.ByNameWrapper
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType}
import izumi.fundamentals.reflection.Tags.Tag

trait WithDITypedRef {
  this: DIUniverseBase
    with WithDISafeType =>

  case class TypedRef[+T](private val v: T, symbol: SafeType) {
    def value: T = v match {
      case d: ByNameWrapper => d.apply().asInstanceOf[T]
      case o => o
    }
  }
  object TypedRef {
    def apply[T: Tag](value: T): TypedRef[T] = TypedRef(value, SafeType.get[T])
    def byName[T: Tag](value: => T): TypedRef[T] = TypedRef((() => value).asInstanceOf[T], SafeType.get[T])
  }

}
