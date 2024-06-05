package izumi.distage

import izumi.distage.model.Locator
import izumi.distage.model.definition.Identifier
import izumi.distage.model.exceptions.runtime.MissingInstanceException
import izumi.distage.model.reflection._
import izumi.reflect.Tag

trait AbstractLocator extends Locator {
  protected def lookupLocalUnsafe(key: DIKey): Option[Any]

  private[distage] final def lookupLocal[T: Tag](key: DIKey): Option[GenericTypedRef[T]] = {
    lookupLocalUnsafe(key)
      .map {
        value =>
          require(key.tpe <:< SafeType.get[T], s"$key in not a subtype of ${SafeType.get[T]}")
          TypedRef(value.asInstanceOf[T], key.tpe, isByName = false)
      }
  }

  override final def find[T: Tag]: Option[T] =
    lookupInstance(DIKey.get[T])

  override final def find[T: Tag](id: Identifier): Option[T] =
    lookupInstance(DIKey.get[T].named(id))

  override final def get[T: Tag]: T =
    lookupInstanceOrThrow(DIKey.get[T])

  override final def get[T: Tag](id: Identifier): T =
    lookupInstanceOrThrow(DIKey.get[T].named(id))

  override final def lookupInstanceOrThrow[T: Tag](key: DIKey): T = {
    lookupRefOrThrow[T](key).value
  }

  override final def lookupInstance[T: Tag](key: DIKey): Option[T] = {
    lookupRef(key).map(_.value)
  }

  override final def lookupRefOrThrow[T: Tag](key: DIKey): GenericTypedRef[T] = {
    lookupRef(key) match {
      case Some(value) =>
        value
      case None =>
        throw new MissingInstanceException(s"Instance is not available in the object graph: $key", key)
    }
  }

  override final def lookupRef[T: Tag](key: DIKey): Option[GenericTypedRef[T]] = {
    recursiveLookup(key, this)
  }

  private final def recursiveLookup[T: Tag](key: DIKey, locator: Locator): Option[GenericTypedRef[T]] = {
    locator
      .lookupLocal[T](key)
      .orElse(locator.parent.flatMap(p => recursiveLookup[T](key, p)))
  }
}
