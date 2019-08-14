package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.references.WithDIKey
import com.github.pshirshov.izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType}

trait WithDIAnnotation {
  this: DIUniverseBase
    with WithDIKey
    with WithDISafeType =>

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
