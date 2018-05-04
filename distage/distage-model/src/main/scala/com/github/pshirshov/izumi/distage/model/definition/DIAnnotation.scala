package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe.{DISafeType, DIUniverseBase}

trait DIAnnotation {
  this: DIUniverseBase
    with DIKey
    with DISafeType =>

  import u._

  object Id {
    def unapply(ann: Annotation): Option[String] = {
      ann.tree.children.tail.collectFirst {
        case Literal(Constant(name: String)) =>
          name
      }
    }
  }

  object With {
    def unapply(ann: Annotation): Option[SafeType] =
      ann.tree.tpe.typeArgs.headOption.map(SafeType(_))
  }


}
