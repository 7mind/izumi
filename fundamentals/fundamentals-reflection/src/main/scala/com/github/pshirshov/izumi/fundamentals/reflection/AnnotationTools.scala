package com.github.pshirshov.izumi.fundamentals.reflection

import scala.reflect.api.Universe

object AnnotationTools {
  def find[T](u: Universe)(symb: u.Symbol)(implicit tTag: u.TypeTag[T]): Option[u.Annotation] = {
    symb
      .annotations
      .find {
        annotationTypeEq(u)(tTag)
      }.orElse {
      symb.typeSignature.finalResultType match {
        case t: u.AnnotatedTypeApi =>
          t.annotations.find {
            annotationTypeEq(u)(tTag)
          }
        case _ =>
          None
      }
    }
  }

  def annotationTypeEq[T](u: Universe)(tTag: u.TypeTag[T])(ann: u.Annotation): Boolean = {
    ann.tree.tpe.erasure =:= tTag.tpe.erasure
  }
}
