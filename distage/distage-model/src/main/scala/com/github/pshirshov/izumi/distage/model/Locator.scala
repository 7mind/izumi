package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.references.{IdentifiedRef, TypedRef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

trait Locator {
  def enumerate: Stream[IdentifiedRef]

  def plan: FinalPlan

  def parent: Option[Locator]

  def lookupInstanceOrThrow[T: RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey): T

  def lookupInstance[T: RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey): Option[T]

  def find[T: RuntimeUniverse.Tag]: Option[T]

  def find[T: RuntimeUniverse.Tag](id: String): Option[T]

  def get[T: RuntimeUniverse.Tag]: T

  def get[T: RuntimeUniverse.Tag](id: String): T

  protected[distage] def lookup[T: RuntimeUniverse.Tag](key: RuntimeUniverse.DIKey): Option[TypedRef[T]]
}

trait LocatorExtension {
  def extend(locator: Locator): Locator
}

object Locator {
  implicit class LocatorExt(locator: Locator) {
    def extend(extensions: LocatorExtension*): Locator = {
      extensions.foldLeft(locator) {
        case (acc, e) =>
          e.extend(acc)
      }
    }
  }
}
