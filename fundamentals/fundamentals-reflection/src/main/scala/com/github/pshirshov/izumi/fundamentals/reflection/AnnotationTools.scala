package com.github.pshirshov.izumi.fundamentals.reflection

import scala.reflect.api.Universe

object AnnotationTools {
  def find(u: Universe)(annType: u.Type, symb: u.Symbol): Option[u.Annotation] = (
    findSymbolAnnotation(u)(annType, symb)
      orElse findTypeAnnotation(u)(annType, symb.typeSignature.finalResultType)
    )

  def findSymbolAnnotation(u: Universe)(annType: u.Type, symb: u.Symbol): Option[u.Annotation] =
      symb.annotations.find(annotationTypeEq(u)(annType, _))

  def findTypeAnnotation(u: Universe)(annType: u.Type, typ: u.Type): Option[u.Annotation] =
    typ match {
      case t: u.AnnotatedTypeApi =>
        t.annotations.find(annotationTypeEq(u)(annType, _))
      case _ =>
        None
    }

  def annotationTypeEq(u: Universe)(tpe: u.Type, ann: u.Annotation): Boolean =
    ann.tree.tpe.erasure =:= tpe.erasure

  def findMatchingBody[T: u.TypeTag, R](u: Universe)(symb: u.Symbol, matcher: PartialFunction[u.Tree, R]): Option[R] =
    find(u)(u.typeOf[T], symb).map(_.tree.children.tail).flatMap(_.collectFirst[R](matcher))

}
