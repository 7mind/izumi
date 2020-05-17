package izumi.fundamentals.reflection

import izumi.fundamentals.reflection.ReflectionUtil.stripByName

import scala.reflect.api.Universe

object AnnotationTools {

  def getAllAnnotations(u: Universe)(symb: u.Symbol): List[u.Annotation] =
    symb.annotations ++ getAllTypeAnnotations(u)(symb.typeSignature)

  def getAllTypeAnnotations(u: Universe)(typ: u.Type): List[u.Annotation] =
    stripByName(u)(typ.finalResultType.dealias) match {
      case t: u.AnnotatedTypeApi =>
        t.annotations
      case _ =>
        Nil
    }

  def annotationTypeEq(u: Universe)(tpe: u.Type, ann: u.Annotation): Boolean =
    ann.tree.tpe.erasure =:= tpe.erasure

}
