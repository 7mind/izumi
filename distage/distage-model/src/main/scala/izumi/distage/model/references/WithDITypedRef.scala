package izumi.distage.model.references

import izumi.distage.model.provisioning.strategies.ByNameDispatcher
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType}
import izumi.fundamentals.reflection.Tags.Tag

trait WithDITypedRef {
  this: DIUniverseBase
    with WithDISafeType =>

  case class TypedRef[+T](private val v: T, symbol: SafeType) {
    def value: T = {
      v match {
        case d: ByNameDispatcher => d.apply().asInstanceOf[T]
        case o => o
      }
    }
  }

  object TypedRef {
    def apply[T: Tag](value: T): TypedRef[T] =
      TypedRef(value, SafeType.get[T])
  }

}
