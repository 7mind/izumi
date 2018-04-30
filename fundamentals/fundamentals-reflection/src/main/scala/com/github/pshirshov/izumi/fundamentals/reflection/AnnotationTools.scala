package com.github.pshirshov.izumi.fundamentals.reflection

import scala.reflect.api.Universe

object AnnotationTools {
  def find[T: u.TypeTag](u: Universe)(symb: u.Symbol): Option[u.Annotation] = (
    findSymbolAnnotation[T](u)(symb)
      orElse findTypeAnnotation[T](u)(symb.typeSignature.finalResultType)
    )

  def findSymbolAnnotation[T: u.TypeTag](u: Universe)(symb: u.Symbol): Option[u.Annotation] =
      symb.annotations.find(annotationTypeEq[T](u)(_))

  def findTypeAnnotation[T: u.TypeTag](u: Universe)(typ: u.Type): Option[u.Annotation] =
    typ match {
      case t: u.AnnotatedTypeApi =>
        t.annotations.find(annotationTypeEq[T](u)(_))
      case _ =>
        None
    }

  def annotationTypeEq[T: u.TypeTag](u: Universe)(ann: u.Annotation): Boolean =
    ann.tree.tpe.erasure =:= implicitly[u.TypeTag[T]].tpe.erasure
}
