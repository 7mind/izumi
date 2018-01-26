package com.github.pshirshov.izumi.distage.commons

import com.github.pshirshov.izumi.distage.{Tag, TypeSymb}

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
