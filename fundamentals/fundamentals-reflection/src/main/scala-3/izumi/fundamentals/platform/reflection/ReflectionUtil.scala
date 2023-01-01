package izumi.fundamentals.platform.reflection

import scala.quoted.Quotes
import scala.collection.mutable

import scala.annotation.tailrec

object ReflectionUtil {

  /**
    * Returns true if the given type contains no type parameters
    * (this means the type is not "weak" https://stackoverflow.com/questions/29435985/weaktypetag-v-typetag)
    *
    * There is another copy of this snippet in izumi-reflect!
    */
  def allPartsStrong(using q: Quotes)(typeRepr: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    typeRepr.dealias match {
      case x if x.typeSymbol.isTypeParam => false
      case x @ TypeRef(ThisType(_), _) if x.typeSymbol.isAbstractType && !x.typeSymbol.isClassDef => false
      case AppliedType(tpe, args) => allPartsStrong(tpe) && args.forall(allPartsStrong(_))
      case AndType(lhs, rhs) => allPartsStrong(lhs) && allPartsStrong(rhs)
      case OrType(lhs, rhs) => allPartsStrong(lhs) && allPartsStrong(rhs)
      case TypeRef(tpe, _) => allPartsStrong(tpe)
      case TermRef(tpe, _) => allPartsStrong(tpe)
      case ThisType(tpe) => allPartsStrong(tpe)
      case NoPrefix() => true
      case TypeBounds(lo, hi) => allPartsStrong(lo) && allPartsStrong(hi)
      case TypeLambda(_, _, body) => allPartsStrong(body)
      case strange => true
    }
  }

  def intersectionMembers(using q: Quotes)(typeRepr: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect.*

    val tpes = mutable.HashSet.empty[TypeRepr]

    def go(t0: TypeRepr): Unit = t0.dealias match {
      case tpe: AndType =>
        go(tpe.left)
        go(tpe.right)
      case t =>
        tpes += t
    }

    go(typeRepr)
    tpes.toList
  }

  def intersectionUnionMembers(using q: Quotes)(typeRepr: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect.*

    val tpes = mutable.HashSet.empty[TypeRepr]

    def go(t0: TypeRepr): Unit = t0.dealias match {
      case tpe: AndType =>
        go(tpe.left)
        go(tpe.right)
      case t =>
        tpes += t
    }

    go(typeRepr)
    tpes.toList
  }

  def readTypeOrSymbolDIAnnotation[A](
    using qctx: Quotes
  )(diAnnotationSym: qctx.reflect.Symbol
  )(name: String,
    annotSym: Option[qctx.reflect.Symbol],
    annotTpe: Either[qctx.reflect.TypeTree, qctx.reflect.TypeRepr],
  )(extractResult: qctx.reflect.Term => Option[A]
  ): Option[A] = {
    import qctx.reflect.*

    (findTypeAnno(annotTpe, diAnnotationSym), annotSym.flatMap(findSymbolAnno(_, diAnnotationSym))) match {
      case (Some(t), Some(s)) =>
        report.errorAndAbort(s"Multiple DI annotations on symbol and type at the same time on symbol=$name, typeAnnotation=${t.show} paramAnnotation=${s.show}")
      case a @ ((Some(_), None) | (None, Some(_))) =>
        val term = a._1.getOrElse(a._2.getOrElse(throw new RuntimeException("impossible")))
        extractResult(term)
      case (None, None) =>
        None
    }
  }

  private def findSymbolAnno(
    using qctx: Quotes
  )(sym: qctx.reflect.Symbol,
    annotationSym: qctx.reflect.Symbol,
  ): Option[qctx.reflect.Term] = {
    sym.getAnnotation(annotationSym)
  }

  private def findTypeAnno(
    using qctx: Quotes
  )(t0: Either[qctx.reflect.TypeTree, qctx.reflect.TypeRepr],
    sym: qctx.reflect.Symbol,
  ): Option[qctx.reflect.Term] = {
    import qctx.reflect.*
    t0 match {
      case Right(t) =>
        findTypeReprAnno(t, sym).orElse(findTypeTreeAnno(TypeTree.of(using t.asType), sym))
      case Left(t) =>
        findTypeTreeAnno(t, sym).orElse(findTypeReprAnno(t.tpe, sym))
    }
  }

  @tailrec private def findTypeReprAnno(
    using qctx: Quotes
  )(t0: qctx.reflect.TypeRepr,
    sym: qctx.reflect.Symbol,
  ): Option[qctx.reflect.Term] = {
    import qctx.reflect.*
    t0 match {
      case AnnotatedType(_, aterm) if aterm.tpe.classSymbol.contains(sym) =>
        Some(aterm)
      case AnnotatedType(t, _) =>
        findTypeReprAnno(t, sym)
      case ByNameType(t) =>
        findTypeReprAnno(t, sym)
      case t =>
        val dealiased = t.dealias.simplified
        if (t.asInstanceOf[AnyRef] eq dealiased.asInstanceOf[AnyRef]) {
          None
        } else {
          findTypeReprAnno(dealiased, sym)
        }
    }
  }

  @tailrec private def findTypeTreeAnno(
    using qctx: Quotes
  )(t: qctx.reflect.TypeTree,
    sym: qctx.reflect.Symbol,
  ): Option[qctx.reflect.Term] = {
    import qctx.reflect.*
    t match {
      case Annotated(_, aterm) if aterm.tpe.classSymbol.contains(sym) =>
        Some(aterm)
      case Annotated(t, _) =>
        findTypeTreeAnno(t, sym)
      case ByName(t) =>
        findTypeTreeAnno(t, sym)
      case _ =>
        None
    }
  }

}
