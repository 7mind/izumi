package izumi.fundamentals.reflection

import scala.reflect.api.Universe

object AnnotationTools {

  def getAllAnnotations(u: Universe)(symb: u.Symbol): List[u.Annotation] = {
    symb.annotations ++ getAllTypeAnnotations[u.type](symb.typeSignature)
  }

  def getAllTypeAnnotations[U <: Universe with Singleton](typ: U#Type): List[U#Annotation] = {
    typ.finalResultType.dealias match {
      case t: U#AnnotatedTypeApi =>
        t.annotations
      case _ =>
        Nil
    }
  }

  def annotationTypeEq(u: Universe)(tpe: u.Type, ann: u.Annotation): Boolean = {
    ann.tree.tpe.erasure =:= tpe.erasure
  }
}
