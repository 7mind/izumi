package com.github.pshirshov.izumi.fundamentals.reflection


import scala.reflect.runtime.universe

object AnnotationTools {
  def find[T: Tag](symb: TypeSymb): Option[universe.Annotation] = {
    symb
      .annotations
      .find {
        ann =>
          ann.tree.tpe.erasure =:= universe.typeOf[T].erasure
      }
  }
}
