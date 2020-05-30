package izumi.distage

import izumi.distage.model.Locator
import izumi.distage.model.definition.ContractedId
import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.reflection._
import izumi.reflect.Tag

trait AbstractLocator extends Locator {
  protected def lookupLocalUnsafe(key: DIKey): Option[Any]

  private[distage] def lookupLocal[T: Tag](key: DIKey): Option[TypedRef[T]] = {
    lookupLocalUnsafe(key)
      .map {
        value =>
          assert(key.tpe <:< SafeType.get[T], s"$key in not a subtype of ${SafeType.get[T]}")
          TypedRef(value.asInstanceOf[T], key.tpe, isByName = false)
      }
  }

  override final def find[T: Tag]: Option[T] =
    lookupInstance(DIKey.get[T])

  override final def find[T: Tag](id: ContractedId[_]): Option[T] =
    lookupInstance(DIKey.get[T].named(id))

  override final def get[T: Tag]: T =
    lookupInstanceOrThrow(DIKey.get[T])

  override final def get[T: Tag](id: ContractedId[_]): T =
    lookupInstanceOrThrow(DIKey.get[T].named(id))

  override final def lookupInstanceOrThrow[T: Tag](key: DIKey): T = {
    lookupRefOrThrow[T](key).value
  }

  override final def lookupInstance[T: Tag](key: DIKey): Option[T] = {
    lookupRef(key).map(_.value)
  }

  override final def lookupRefOrThrow[T: Tag](key: DIKey): TypedRef[T] = {
    lookupRef(key) match {
      case Some(value) =>
        value
      case None =>
        throw new MissingInstanceException(s"Instance is not available in the object graph: $key", key)
    }
  }

  override final def lookupRef[T: Tag](key: DIKey): Option[TypedRef[T]] = {
    recursiveLookup(key, this)
  }

  private[this] final def recursiveLookup[T: Tag](key: DIKey, locator: Locator): Option[TypedRef[T]] = {
    locator.lookupLocal[T](key)
      .orElse(locator.parent.flatMap(p => recursiveLookup(key, p)))
  }
}
