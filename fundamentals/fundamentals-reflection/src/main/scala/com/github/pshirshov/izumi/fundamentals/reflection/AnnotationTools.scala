package com.github.pshirshov.izumi.fundamentals.reflection

import scala.reflect.api.Universe

object AnnotationTools {
  def find[T](u: Universe)(symb: u.Symbol)(implicit tTag: u.TypeTag[T]): Option[u.Annotation] = {
    symb
      .annotations
      .find {
        ann =>
          ann.tree.tpe.erasure =:= tTag.tpe.erasure
      }
  }
}
