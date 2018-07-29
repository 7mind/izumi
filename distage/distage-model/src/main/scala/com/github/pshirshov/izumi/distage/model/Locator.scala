package com.github.pshirshov.izumi.distage.model

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

/** Holds the object graph created by executing a `plan`
  *
  * @see [[Injector]]
  * @see [[Planner]]
  * @see [[Producer]]
  **/
trait Locator {
  /** Instances in order of creation **/
  def instances: Seq[IdentifiedRef]

  def plan: OrderedPlan

  def parent: Option[Locator]

  def lookupInstanceOrThrow[T: Tag](key: DIKey): T

  def lookupInstance[T: Tag](key: DIKey): Option[T]

  def find[T: Tag]: Option[T]

  def find[T: Tag](id: String): Option[T]

  def get[T: Tag]: T

  def get[T: Tag](id: String): T

  protected[distage] def lookup[T: Tag](key: DIKey): Option[TypedRef[T]]
}

object Locator {
  class LocatorRef() {
    protected[distage] val ref: AtomicReference[Locator] = new AtomicReference[Locator]()
    def get: Locator = ref.get()
  }

  implicit final class LocatorExt(private val locator: Locator) extends AnyVal {
    def extend(extensions: LocatorExtension*): Locator =
      extensions.foldLeft(locator) {
        case (acc, ext) =>
          ext.extend(acc)
      }
  }

}
