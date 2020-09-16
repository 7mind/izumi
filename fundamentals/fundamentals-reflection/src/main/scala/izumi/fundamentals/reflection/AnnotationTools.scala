package izumi.fundamentals.reflection

import izumi.fundamentals.reflection.ReflectionUtil.stripByName

import scala.reflect.api.Universe

object AnnotationTools {

  def getAllAnnotations(u: Universe)(symb: u.Symbol): List[u.Annotation] = {
    val out = symb.annotations ++ getAllTypeAnnotations(u)(symb.typeSignature)
    println(s"AE0: $symb => $out ${symb.typeSignature}")
    out
  }

  def getAllTypeAnnotations(u: Universe)(typ: u.Type): List[u.Annotation] = {
    val out = stripByName(u)(typ.finalResultType.dealias) match {
      case t: u.AnnotatedTypeApi =>
        t.annotations
      case _ =>
        Nil
    }
    println(s"AE1: $typ => ${typ.finalResultType.dealias} $out")
    out
  }

  def annotationTypeEq(u: Universe)(tpe: u.Type, ann: u.Annotation): Boolean = {
    ann.tree.tpe.erasure =:= tpe.erasure
  }
}
