package izumi.distage.model.providers

import izumi.distage.model.reflection.Provider.{ProviderImpl, ProviderType}
import izumi.distage.model.reflection.*
import izumi.distage.constructors.ClassConstructorMacro
import izumi.reflect.Tag
import izumi.distage.model.definition.Id

import scala.annotation.{tailrec, targetName}
import scala.collection.immutable.List
import scala.language.implicitConversions
import scala.quoted.{Expr, Quotes, Type}

trait FunctoidMacroMethods {
  import FunctoidMacro.make

  inline implicit def apply[R](inline fun: () => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)

  // 23 to 32
  @targetName("apply23")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  @targetName("apply24")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  @targetName("apply25")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  @targetName("apply26")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  @targetName("apply27")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  @targetName("apply28")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  @targetName("apply29")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  @targetName("apply30")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] = make[R](fun)
  @targetName("apply31")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] =
    make[R](fun)
  @targetName("apply32")
  inline implicit def apply[R](inline fun: (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) => R): Functoid[R] =
    make[R](fun)
}

object FunctoidMacro {
  inline def make[R](inline fun: Any): Functoid[R] = ${ makeImpl[R]('fun) }

  def makeImpl[R: Type](fun: Expr[Any])(using qctx: Quotes): Expr[Functoid[R]] = new FunctoidMacroImpl[qctx.type]().make(fun)

  final class FunctoidParametersMacro[Q <: Quotes](using val qctx: Q) {
    import qctx.reflect.*

    val idAnnotationSym: Symbol = TypeRepr.of[Id].typeSymbol

    extension (t: Either[TypeTree, TypeRepr]) {
      def _tpe: TypeRepr = t match {
        case Right(t) => t
        case Left(t) => t.tpe
      }
    }

    def makeParams[R: Type](params: List[(String, Option[Symbol], Either[TypeTree, TypeRepr])]): List[Expr[LinkedParameter]] = {
      params.map {
        case (name, sym, tpe) =>
          val identifier = (findTypeAnno(tpe, idAnnotationSym), sym.flatMap(findSymbolAnno(_, idAnnotationSym))) match {
            case (Some(_), Some(_)) =>
              report.errorAndAbort("conflicting annotations on type and parameter at the same time")
            case a @ ((Some(_), None) | (None, Some(_))) =>
              a._1.getOrElse(a._2.getOrElse(throw new RuntimeException("impossible"))) match {
                case aterm @ Apply(Select(New(_), _), c :: _) =>
                  c.asExprOf[String].value.orElse {
                    report.errorAndAbort(s"distage.Id annotation expects one literal argument but got ${c.show} in tree ${aterm.show} ($aterm)")
                  }
                case aterm =>
                  report.errorAndAbort(s"distage.Id annotation expects one literal argument but got malformed tree ${aterm.show} ($aterm)")
              }
//            case _ if tpe.toString.contains("Annot") =>
//              given Printer[Tree] = Printer.TreeStructure
//              report.errorAndAbort(s"Not annotated $tpe show=${tpe.fold(_.show, _.show)} show-dealias=${tpe.fold(_.show, _.dealias.show)} wtf=${tpe match {
//                  case Right(t @ AnnotatedType(_, term)) => term.show -> findTypeReprAnno(t, idAnnotationSym)
//                  case Right(t) => s"t=$t"
//                  case Left(t) => s"tpeRepr=${tpe._tpe} tpeReprShow=${tpe._tpe.show}"
//                }}")
            case (None, None) =>
              None
          }

          val tpeRepr = tpe._tpe

          val isByName = tpeRepr match {
            case ByNameType(_) => true
            case _ => false
          }

          '{
            LinkedParameter(
              SymbolInfo(
                name = ${ Expr(name) },
                finalResultType = ${ safeTypeFromRepr(tpeRepr) },
                isByName = ${ Expr(isByName) },
                wasGeneric = ${ Expr(tpeRepr.typeSymbol.isTypeParam) }, // deem abstract type members as generic? No. Because we don't do that in Scala 2 version.
              ),
              ${ makeKeyFromRepr(tpeRepr, identifier) },
            )
          }
      }
    }

//    def makeKey(tpe: TypeTree, id: Option[String]): Expr[DIKey] = {
//      makeKeyFromRepr(tpe.tpe, id)
//    }

    def safeType[R: Type]: Expr[SafeType] = {
      '{ SafeType.get[R](using scala.compiletime.summonInline[Tag[R]]) }
    }

    private def makeKeyFromRepr(tpe: TypeRepr, id: Option[String]): Expr[DIKey] = {
      val safeTpe = safeTypeFromRepr(tpe)
      id match {
        case Some(str) =>
          val strExpr = Expr(str)
          '{ new DIKey.IdKey($safeTpe, $strExpr, None)(scala.compiletime.summonInline[IdContract[String]]) }
        case None =>
          '{ new DIKey.TypeKey($safeTpe) }
      }
    }

//    private def safeType(tpe: TypeTree): Expr[SafeType] = {
//      safeTypeFromRepr(tpe.tpe)
//    }

    private def safeTypeFromRepr(tpe: TypeRepr): Expr[SafeType] = {
      dropByName(tpe).asType match {
        case '[a] =>
          '{ SafeType.get[a](using scala.compiletime.summonInline[Tag[a]]) }
        case _ =>
          report.errorAndAbort(s"Cannot generate SafeType from ${tpe.show}, probably that's a bug in Functoid macro")
      }
    }

    private def dropByName(tpe: TypeRepr): TypeRepr = {
      tpe match {
        case ByNameType(u) => u
        case _ => tpe
      }
    }

    def findSymbolAnno(sym: Symbol, annotationSym: Symbol): Option[Term] = {
      sym.getAnnotation(annotationSym)
    }

    def findTypeAnno(t0: Either[TypeTree, TypeRepr], sym: Symbol): Option[Term] = {
      t0 match {
        case Right(t) =>
          findTypeReprAnno(t, sym).orElse(findTypeTreeAnno(TypeTree.of(using t.asType), sym))
        case Left(t) =>
          findTypeTreeAnno(t, sym).orElse(findTypeReprAnno(t.tpe, sym))
      }
    }

    @tailrec private def findTypeReprAnno(t0: TypeRepr, sym: Symbol): Option[Term] = t0 match {
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

    @tailrec private def findTypeTreeAnno(t: TypeTree, sym: Symbol): Option[Term] = t match {
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

  final class FunctoidMacroImpl[Q <: Quotes](using val qctx: Q) {

    import qctx.reflect.*

    private val paramsMacro = new FunctoidParametersMacro[qctx.type]()

    def make[R: Type](fun: Expr[Any]): Expr[Functoid[R]] = {
      val out = matchTerm[R](fun.asTerm)
      report.warning(
        s"""fun=${fun.show}
           |funType=${fun.asTerm.tpe}
           |funSym=${fun.asTerm.symbol}
           |funTypeSym=${fun.asTerm.tpe.typeSymbol}
           |funTypeSymBases=${fun.asTerm.tpe.baseClasses}
           |outputType=${Type.show[R]}
           |rawOutputType=(${TypeRepr.of[R]})
           |produced=${out.show}""".stripMargin
      )
      out
    }

    @tailrec
    def matchTerm[R: Type](fun: Term): Expr[Functoid[R]] = {
      fun match {
        case Block(List(defdef), Closure(_, _)) =>
          matchStatement[R](defdef, fun)
        case Typed(term, _) =>
          matchTerm(term)
        case Inlined(_, _, term) =>
          matchTerm(term)
        case Block(_, term) =>
          matchTerm(term)
        case _ =>
          val args = fun.underlying.tpe.typeArgs match {
            case Nil => Nil
            case o => o.init
          }
          analyzeParams[R](args.iterator.zipWithIndex.map((tpe, idx) => (s"arg_$idx", None, Right(tpe))).toList, fun)
      }
    }

    def matchStatement[R: Type](statement: Statement, fun: Term): Expr[Functoid[R]] = {
      statement match {
        case DefDef(name, params :: Nil, _, _) =>
          val paramTypes = params.params.map {
            case tree @ ValDef(name, tpe, _) =>
              (name, Some(tree.symbol).filterNot(_.isNoSymbol), Left(tpe))
            case p =>
              report.errorAndAbort(s"Unexpected parameter in $name: ${p.show}")
          }
          analyzeParams[R](paramTypes, fun)
      }
    }

    def analyzeParams[R: Type](params: List[(String, Option[Symbol], Either[TypeTree, TypeRepr])], fun: Term): Expr[Functoid[R]] = {
      val paramDefs = paramsMacro.makeParams[R](params)

      '{
        val rawFn: AnyRef = ${ fun.asExprOf[AnyRef] }
        new Functoid[R](
          new ProviderImpl[R](
            ${ Expr.ofList(paramDefs) },
            ${ paramsMacro.safeType[R] },
            rawFn,
            (args: Seq[Any]) => ${ generateCall(params.size, 'rawFn, 'args) },
            ProviderType.Function,
          )
        )
      }
    }

    def generateCall(argsNum: Int, rawFn: Expr[Any], args: Expr[Seq[Any]]): Expr[Any] = {
      val params = (0 until argsNum).map {
        idx =>
          '{ $args(${ Expr(idx) }) }
      }.toList

      val fnType = defn.FunctionClass(argsNum).typeRef.appliedTo((0 to argsNum).map(_ => TypeRepr.of[Any]).toList)

      val fnAny = fnType.asType match {
        case '[a] =>
          '{ ${ rawFn.asExprOf[Any] }.asInstanceOf[a] }
        case _ =>
          report.errorAndAbort(s"This is totally unexpected: ${fnType.show} type is higher-kinded type constructor, but expected a proper type")
      }

      Select.unique(fnAny.asTerm, "apply").appliedToArgs(params.map(_.asTerm)).asExprOf[Any]
    }

  }

}
