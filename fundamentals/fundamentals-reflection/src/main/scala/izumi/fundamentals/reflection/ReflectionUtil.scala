package izumi.fundamentals.reflection

import scala.annotation.tailrec
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

object ReflectionUtil {

  /** Mini `normalize`. `normalize` is deprecated and we don't want to do scary things such as evaluate type-lambdas anyway.
    * And AFAIK the only case that can make us confuse a type-parameter for a non-parameter is an empty refinement `T {}`.
    * So we just strip it when we get it.
    */
  @tailrec
  final def norm(u: Universe)(x: u.Type): u.Type = {
    import u._
    x match {
      case RefinedType(t :: Nil, m) if m.isEmpty => norm(u)(t)
      case AnnotatedType(_, t) => norm(u)(t)
      case _ => x
    }
  }

  /** Mini `normalize`. `normalize` is deprecated and we don't want to do scary things such as evaluate type-lambdas anyway.
    * And AFAIK the only case that can make us confuse a type-parameter for a non-parameter is an empty refinement `T {}`.
    * So we just strip it when we get it.
    */
  @tailrec
  final def norm0(u: Universe)(x: u.Type): u.Type = {
    x match {
      case r: u.RefinedTypeApi if (r.parents.drop(1) eq Nil) && r.decls.isEmpty => norm0(u)(r.asInstanceOf[u.Type])
      case a: u.AnnotatedTypeApi => norm0(u)(a.underlying)
      case _ => x
    }
  }

  def toTypeRef(u: Universe)(tpe: u.TypeApi): Option[u.TypeRefApi] = {
    tpe match {
      case typeRef: u.TypeRefApi =>
        Some(typeRef)
      case _ =>
        None
    }
  }

  def stripByName(u: Universe)(tpe: u.Type): u.Type = {
    if (isByName(u)(tpe)) tpe.typeArgs.head.finalResultType else tpe
  }

  def isByName(u: Universe)(tpe: u.Type): Boolean = {
    tpe.typeSymbol.isClass && tpe.typeSymbol.asClass == u.definitions.ByNameParamClass
  }

  def allPartsStrong(tpe: Universe#Type): Boolean = {
    val selfStrong = isSelfStrong(tpe)
    def prefixStrong = {
      tpe match {
        case t: Universe#TypeRefApi =>
          allPartsStrong(t.pre.dealias)
        case _ =>
          true
      }
    }
    def argsStrong = {
      tpe.dealias.finalResultType.typeArgs.forall {
        arg =>
          tpe.typeParams.contains(arg.typeSymbol) ||
          allPartsStrong(arg)
      }
    }
    def intersectionStructStrong = {
      tpe match {
        case t: Universe#RefinedTypeApi =>
          t.parents.forall(allPartsStrong) &&
          t.decls.toSeq.forall((s: Universe#Symbol) => s.isTerm || allPartsStrong(s.asType.typeSignature.dealias))
        case _ =>
          true
      }
    }

    selfStrong && prefixStrong && argsStrong && intersectionStructStrong
  }

  def isSelfStrong(tpe: Universe#Type): Boolean = {
    !(tpe.typeSymbol.isParameter || (
      tpe.isInstanceOf[Universe#TypeRefApi] &&
      tpe.asInstanceOf[Universe#TypeRefApi].pre.isInstanceOf[Universe#ThisTypeApi] &&
      tpe.typeSymbol.isAbstract && !tpe.typeSymbol.isClass && isNotDealiasedFurther(tpe)
    )) ||
    tpe.typeParams.exists {
      t =>
        t == tpe.typeSymbol ||
        t.typeSignature == tpe.typeSymbol.typeSignature ||
        (t.name eq tpe.typeSymbol.name)
    }
  }

  def isNotDealiasedFurther(tpe: Universe#Type): Boolean = {
    val u: Universe = null
    val tpe1: u.Type = tpe.asInstanceOf[u.Type]
    tpe1.dealias =:= tpe1
  }

  def deepIntersectionTypeMembers(u: Universe)(targetType: u.Type): List[u.Type] = {
    def go(tpe: u.Type): List[u.Type] = {
      tpe match {
        case r: u.RefinedTypeApi => r.parents.flatMap(t => deepIntersectionTypeMembers(u)(norm0(u)(t.dealias)))
        case _ => List(tpe)
      }
    }
    go(targetType).distinct
  }

  def getStringLiteral(c: blackbox.Context)(tree: c.universe.Tree): String = {
    findStringLiteral(tree).getOrElse(c.abort(c.enclosingPosition, "must use string literal"))
  }

  def findStringLiteral(tree: Universe#Tree): Option[String] = {
    tree.collect {
      case l: Universe#LiteralApi if l.value.value.isInstanceOf[String] =>
        l.value.value.asInstanceOf[String]
    }.headOption
  }

}
