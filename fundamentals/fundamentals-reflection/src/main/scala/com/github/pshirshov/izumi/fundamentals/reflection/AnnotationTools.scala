package com.github.pshirshov.izumi.fundamentals.reflection


object AnnotationTools {
  def find[T: RuntimeUniverse.Tag](symb: RuntimeUniverse.TypeSymb): Option[RuntimeUniverse.u.Annotation] = {
    symb
      .annotations
      .find {
        ann =>
          ann.tree.tpe.erasure =:= RuntimeUniverse.u.typeOf[T].erasure
      }
  }
}
