package izumi.distage.model.references

import izumi.distage.model.provisioning.strategies.ByNameDispatcher
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType, WithTags}

trait WithDITypedRef {
  this: DIUniverseBase with WithDISafeType with WithTags =>

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
