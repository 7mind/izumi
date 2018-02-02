package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.references.{DIKey, IdentifiedRef, TypedRef}
import com.github.pshirshov.izumi.fundamentals.reflection.RuntimeUniverse

trait Locator {
  def enumerate: Stream[IdentifiedRef]

  def plan: FinalPlan

  def parent: Option[Locator]

  def lookupInstanceOrThrow[T: RuntimeUniverse.Tag](key: DIKey): T

  def lookupInstance[T: RuntimeUniverse.Tag](key: DIKey): Option[T]

  def find[T: RuntimeUniverse.Tag]: Option[T]

  def find[T: RuntimeUniverse.Tag](id: String): Option[T]

  def get[T: RuntimeUniverse.Tag]: T

  def get[T: RuntimeUniverse.Tag](id: String): T

  protected[distage] def lookup[T: RuntimeUniverse.Tag](key: DIKey): Option[TypedRef[T]]
}
