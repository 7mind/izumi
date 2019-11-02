package izumi.distage

import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.Locator

trait AbstractLocator extends Locator {
  protected def unsafeLookup(key: DIKey): Option[Any]

  protected[distage] def lookup[T: Tag](key: DIKey): Option[TypedRef[T]] = {
    unsafeLookup(key)
      .filter(_ => key.tpe <:< SafeType.get[T])
      .map {
        value =>
          TypedRef[T](value.asInstanceOf[T])
      }
  }

  final def find[T: Tag]: Option[T] =
    lookupInstance(DIKey.get[T])

  final def find[T: Tag](id: String): Option[T] =
    lookupInstance(DIKey.get[T].named(id))

  final def get[T: Tag]: T =
    lookupInstanceOrThrow(DIKey.get[T])

  final def get[T: Tag](id: String): T =
    lookupInstanceOrThrow(DIKey.get[T].named(id))

  final def lookupInstanceOrThrow[T: Tag](key: DIKey): T = {
    lookupInstance(key) match {
      case Some(value) =>
        value
      case None =>
        throw new MissingInstanceException(s"Instance is not available in the object graph: $key", key)
    }
  }

  final def lookupInstance[T: Tag](key: DIKey): Option[T] = {
    recursiveLookup(key, this)
      .map(_.value)
  }

  private[this] final def recursiveLookup[T: Tag](key: DIKey, locator: Locator): Option[TypedRef[T]] = {
    locator.lookup[T](key)
      .orElse(locator.parent.flatMap(p => recursiveLookup(key, p)))
  }
}
