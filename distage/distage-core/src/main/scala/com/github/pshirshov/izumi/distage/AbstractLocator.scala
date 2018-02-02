package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.references.{DIKey, TypedRef}
import com.github.pshirshov.izumi.distage.model.{Locator, LookupInterceptor}
import com.github.pshirshov.izumi.fundamentals.reflection.RuntimeUniverse

import scala.reflect.runtime.universe._


trait AbstractLocator extends Locator {
  protected def unsafeLookup(key: DIKey): Option[Any]

  protected[distage] def lookup[T: RuntimeUniverse.Tag](key: DIKey): Option[TypedRef[T]] = {
    unsafeLookup(key)
      .filter(_ => key.symbol.tpe.baseClasses.contains(typeTag[T].tpe.typeSymbol))
      .map {
        value =>
          TypedRef[T](value.asInstanceOf[T])
      }
  }

  final def find[T: RuntimeUniverse.Tag]: Option[T] =
    lookupInstance(DIKey.get[T])

  final def find[T: RuntimeUniverse.Tag](id: String): Option[T] =
    lookupInstance(DIKey.get[T].named(id))

  final def get[T: RuntimeUniverse.Tag]: T =
    lookupInstanceOrThrow(DIKey.get[T])

  final def get[T: RuntimeUniverse.Tag](id: String): T =
    lookupInstanceOrThrow(DIKey.get[T].named(id))

  final def lookupInstanceOrThrow[T: RuntimeUniverse.Tag](key: DIKey): T = {
    lookupInstance(key)
      .getOrElse {
        throw new MissingInstanceException(s"Instance is not available in the context: $key", key)
      }
  }

  final def lookupInstance[T: RuntimeUniverse.Tag](key: DIKey): Option[T] = {
    recursiveLookup(key)
      .map(_.value)
  }

  protected final def recursiveLookup[T: RuntimeUniverse.Tag](key: DIKey): Option[TypedRef[T]] = {
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
