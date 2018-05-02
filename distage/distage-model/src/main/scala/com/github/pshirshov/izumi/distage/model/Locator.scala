package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

trait Locator {
  def enumerate: Stream[IdentifiedRef]

  def plan: FinalPlan

  def parent: Option[Locator]

  def lookupInstanceOrThrow[T: Tag](key: DIKey): T

  def lookupInstance[T: Tag](key: DIKey): Option[T]

  def find[T: Tag]: Option[T]

  def find[T: Tag](id: String): Option[T]

  def get[T: Tag]: T

  def get[T: Tag](id: String): T

  protected[distage] def lookup[T: Tag](key: DIKey): Option[TypedRef[T]]
}

trait LocatorExtension {
  def extend(locator: Locator): Locator
}

object Locator {

  implicit final class LocatorExt(private val locator: Locator) extends AnyVal {
    def extend(extensions: LocatorExtension*): Locator = {
      extensions.foldLeft(locator) {
        case (acc, e) =>
          e.extend(acc)
      }
    }
  }

}
