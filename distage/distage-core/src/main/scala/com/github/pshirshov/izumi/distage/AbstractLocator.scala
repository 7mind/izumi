package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.exceptions.MissingInstanceException
import com.github.pshirshov.izumi.distage.model.references.TypedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.model.{Locator, LookupInterceptor}

import scala.reflect.runtime.universe._


trait AbstractLocator extends Locator {
  protected def unsafeLookup(key: RuntimeUniverse.DIKey): Option[Any]

  protected[distage] def lookup[T: RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey): Option[TypedRef[T]] = {
    unsafeLookup(key)
      .filter(_ => key.symbol.tpe.baseClasses.contains(typeTag[T].tpe.typeSymbol))
      .map {
        value =>
          TypedRef[T](value.asInstanceOf[T])
      }
  }

  final def find[T: RuntimeUniverse.Tag]: Option[T] =
    lookupInstance(RuntimeUniverse.DIKey.get[T])

  final def find[T: RuntimeUniverse.Tag](id: String): Option[T] =
    lookupInstance(RuntimeUniverse.DIKey.get[T].named(id))

  final def get[T: RuntimeUniverse.Tag]: T =
    lookupInstanceOrThrow(RuntimeUniverse.DIKey.get[T])

  final def get[T: RuntimeUniverse.Tag](id: String): T =
    lookupInstanceOrThrow(RuntimeUniverse.DIKey.get[T].named(id))

  final def lookupInstanceOrThrow[T: RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey): T = {
    lookupInstance(key)
      .getOrElse {
        throw new MissingInstanceException(s"Instance is not available in the context: $key", key)
      }
  }

  final def lookupInstance[T: RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey): Option[T] = {
    recursiveLookup(key)
      .map(_.value)
  }

  protected final def recursiveLookup[T: RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey): Option[TypedRef[T]] = {
    interceptor.interceptLookup[T](key, this).orElse(
      lookup[T](key)
        .orElse(parent.flatMap(_.lookup(key)))
    )
  }

  protected final def interceptor: LookupInterceptor =
    lookup[LookupInterceptor](RuntimeUniverse.DIKey.get[LookupInterceptor])
      .map(_.value)
      .getOrElse(NullLookupInterceptor)
}
