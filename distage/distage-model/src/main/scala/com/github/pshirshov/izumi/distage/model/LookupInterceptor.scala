package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.references.{DIKey, TypedRef}
import com.github.pshirshov.izumi.fundamentals.reflection._

trait LookupInterceptor {
  def interceptLookup[T:RuntimeUniverse.Tag](key: DIKey, context: Locator): Option[TypedRef[T]]
}

