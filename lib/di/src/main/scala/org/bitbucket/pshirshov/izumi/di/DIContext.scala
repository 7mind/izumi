package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.exceptions.MissingInstanceException

import scala.reflect.runtime.universe

trait DIContext {

  def find[T:Tag]: Option[T] = lookupInstance(DIKey.get[T])

  def find[T:Tag, Id](id: Id): Option[T] = lookupInstance(DIKey.get[T].narrow(id))

  def get[T: Tag]: T = lookupInstanceOrThrow(DIKey.get[T])

  def get[T: Tag, Id](id: Id): T = lookupInstanceOrThrow(DIKey.get[T].narrow(id))

  protected def lookup(key: DIKey): Option[AnyRef]

  protected def lookupInstanceOrThrow[T: Tag](key: DIKey): T = {
    lookupInstance(key) match {
      case Some(v) =>
        v

      case None =>
        throw new MissingInstanceException(s"Instance is not available in the context: $key", key)
    }
  }

  protected def lookupInstance[T: Tag](key: DIKey): Option[T] = {
    lookup(key)
      .filter(t => isInstanceOf(key, t))
      .map(_.asInstanceOf[T])
  }

  protected def mirror: universe.Mirror = universe.runtimeMirror(getClass.getClassLoader)

  protected def isInstanceOf[T: Tag](key: DIKey, t: AnyRef): Boolean = {
    mirror.runtimeClass(key.symbol.asClass).isAssignableFrom(t.getClass)
  }
}

trait InstantiatedContext {
  def subInjector(): Planner
}
