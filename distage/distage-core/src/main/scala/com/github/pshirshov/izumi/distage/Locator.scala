package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.{DIKey, EqualitySafeType}

import scala.reflect.runtime.universe._

case class TypedRef[+T: Tag](value: T) {
  def symbol: TypeFull = EqualitySafeType.get[T]
}

case class IdentifiedRef(key: DIKey, value: Any)

trait Locator {
  def enumerate: Stream[IdentifiedRef]

  def plan: FinalPlan

  def parent: Option[Locator]

  protected def unsafeLookup(key: DIKey): Option[Any]

  protected def lookup[T: Tag](key: DIKey): Option[TypedRef[T]] = {
    unsafeLookup(key)
      .filter(_ => key.symbol.tpe.baseClasses.contains(typeTag[T].tpe.typeSymbol))
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
    lookupInstance(key)
      .getOrElse {
        throw new MissingInstanceException(s"Instance is not available in the context: $key", key)
      }
  }

  final def lookupInstance[T: Tag](key: DIKey): Option[T] = {
    recursiveLookup(key)
      .map(_.value)
  }

  protected final def recursiveLookup[T: Tag](key: DIKey): Option[TypedRef[T]] = {
    interceptor.interceptLookup[T](key, this).orElse(
      lookup(key)
        .orElse(parent.flatMap(_.lookup(key)))
    )
  }

  protected final def interceptor: LookupInterceptor =
    lookup[LookupInterceptor](DIKey.get[LookupInterceptor])
      .map(_.value)
      .getOrElse(NullLookupInterceptor.instance)
}
