package izumi.distage.model.providers

import izumi.distage.model.reflection.Provider.{ProviderImpl, ProviderType}
import izumi.distage.model.reflection.*
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
  inline def make[R](inline fun: AnyRef): Functoid[R] = ${ makeImpl[R]('fun) }

  def makeImpl[R: Type](fun: Expr[AnyRef])(using qctx: Quotes): Expr[Functoid[R]] = new FunctoidMacroImpl[qctx.type]().make(fun)

  final class FunctoidMacroImpl[Q <: Quotes](using val qctx: Q) {

    import qctx.reflect.*

    private val paramsMacro = new FunctoidParametersMacro[qctx.type]()

    def make[R: Type](fun: Expr[AnyRef]): Expr[Functoid[R]] = {
      val parameters = analyze(fun.asTerm)
      val out = generateFunctoid[R](parameters, fun)
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

    @tailrec def analyze(fun: Term): List[Expr[LinkedParameter]] = fun match {
      case Block(List(DefDef(name, (singleParamList: TermParamClause) :: Nil, _, Some(body))), Closure(_, _)) =>
        analyzeLambdaOrMethodRef(name, singleParamList, body)
      case Typed(term, _) =>
        analyze(term)
      case Inlined(_, _, term) =>
        analyze(term)
      case Block(List(), term) =>
        analyze(term)
      case otherExpr =>
        analyzeTypeOfExpr(otherExpr)
    }

    // seems redundant since type annotations are preserved in type of lambda
    private def analyzeLambdaOrMethodRef(name: String, singleParamList: TermParamClause, body: Term): List[Expr[LinkedParameter]] = {
      val methodRefParams = {
        def go(t: Tree): List[Symbol] = t match {
          case Apply(f, args) if args.map(_.symbol) == singleParamList.params.map(_.symbol) =>
            f.symbol.paramSymss.filterNot(_.headOption.exists(_.isTypeParam)).flatten
          case Inlined(_, _, term) => go(term)
          case Block(List(), term) => go(term)
          case Typed(term, _) => go(term)
          case _ => Nil
        }
        go(body)
      }

      val annotationsOnMethodAreNonEmptyAndASuperset = {
        methodRefParams.sizeCompare(singleParamList.params) == 0
        && methodRefParams.exists(_.annotations.nonEmpty)
      }

//      report.info(
//        s"""mrefparams = $methodRefParams
//           |termclause = $singleParamList
//           |body=$body
//           |sym=${body match { case Apply(f, _) => f.symbol -> f.symbol.paramSymss; case _ => None }}
//           |verdict=$annotationsOnMethodAreNonEmptyAndASuperset
//           |""".stripMargin
//      )

      // if method reference has more annotations, get parameters from reference instead
      // to preserve annotations!
      if (annotationsOnMethodAreNonEmptyAndASuperset) {
        // Use types from the generated lambda, not the method reference, because method reference types maybe generic/unresolved/unrelated
        // But lambda params should be sufficiently 'grounded' at this point
        // (Besides, lambda types are the ones specified by the caller, we should respect them)
        singleParamList.params.zip(methodRefParams).map {
          case (ValDef(name, tpeTree, _), mSym) =>
            paramsMacro.makeParam(name, Left(tpeTree), Some(mSym), Right(mSym.owner.typeRef.memberType(mSym)))
        }
      } else {
        singleParamList.params.map {
          case valDef @ ValDef(name, tpeTree, _) =>
            paramsMacro.makeParam(name, Left(tpeTree), Some(valDef.symbol).filterNot(_.isNoSymbol))
        }
      }
    }

    private def analyzeTypeOfExpr(other: Term): List[Expr[LinkedParameter]] = {
      val rawTpe = other.underlying.tpe
      val functionTpe = rawTpe // TODO .baseType

      functionTpe.typeArgs match {
        case Nil => Nil
        case o =>
          val args = o.init
          args.iterator.zipWithIndex.map {
            (tpe, idx) =>
              paramsMacro.makeParam(s"arg_$idx", Right(tpe), None)
          }.toList
      }
    }

    private def generateFunctoid[R: Type](paramDefs: List[Expr[LinkedParameter]], originalFun: Expr[AnyRef]): Expr[Functoid[R]] = {
      '{
        val rawFn: AnyRef = ${ originalFun }
        new Functoid[R](
          new ProviderImpl[R](
            ${ Expr.ofList(paramDefs) },
            ${ paramsMacro.safeType[R] },
            rawFn,
            (args: Seq[Any]) => ${ generateRawFnCall(paramDefs.size, 'rawFn, 'args) },
            ProviderType.Function,
          )
        )
      }
    }

    private def generateRawFnCall(argsCount: Int, rawFn: Expr[Any], args: Expr[Seq[Any]]): Expr[Any] = {
      val params = List.tabulate(argsCount) {
        idx =>
          '{ $args(${ Expr(idx) }) }
      }

      val anyTpe = TypeRepr.of[Any]
      val fnType = defn.FunctionClass(argsCount).typeRef.appliedTo(List.fill(argsCount + 1)(anyTpe))

      val fnAny = fnType.asType match {
        case '[a] =>
          '{ ${ rawFn.asExprOf[Any] }.asInstanceOf[a] }
        case _ =>
          report.errorAndAbort(s"This is totally unexpected: ${fnType.show} type is higher-kinded type constructor, but expected a proper type")
      }

      Select.unique(fnAny.asTerm, "apply").appliedToArgs(params.map(_.asTerm)).asExprOf[Any]
    }

  }

  final class FunctoidParametersMacro[Q <: Quotes](using val qctx: Q) {
    import qctx.reflect.*

    private val idAnnotationSym: Symbol = TypeRepr.of[Id].typeSymbol

    extension (t: Either[TypeTree, TypeRepr]) {
      private def _tpe: TypeRepr = t match {
        case Right(t) => t
        case Left(t) => t.tpe
      }
    }

    def makeParam(name: String, tpe: Either[TypeTree, TypeRepr], annotSym: Option[Symbol]): Expr[LinkedParameter] = {
      makeParam(name, tpe, annotSym, tpe)
    }
    def makeParam(name: String, tpe: Either[TypeTree, TypeRepr], annotSym: Option[Symbol], annotTpe: Either[TypeTree, TypeRepr]): Expr[LinkedParameter] = {
      val identifier = (findTypeAnno(annotTpe, idAnnotationSym), annotSym.flatMap(findSymbolAnno(_, idAnnotationSym))) match {
        case (Some(t), Some(s)) =>
          report.errorAndAbort(s"Multiple DI annotations on symbol and type at the same time on parameter=$name, typeAnnotation=${t.show} paramAnnotation=${s.show}")
        case a @ ((Some(_), None) | (None, Some(_))) =>
          a._1.getOrElse(a._2.getOrElse(throw new RuntimeException("impossible"))) match {
            case aterm @ Apply(Select(New(_), _), c :: _) =>
              c.asExprOf[String].value.orElse {
                report.errorAndAbort(s"distage.Id annotation expects one literal String argument but got ${c.show} in tree ${aterm.show} ($aterm)")
              }
            case aterm =>
              report.errorAndAbort(s"distage.Id annotation expects one literal String argument but got malformed tree ${aterm.show} ($aterm)")
          }
//            case _ if annotTpe.toString.contains("Annot") =>
//              given Printer[Tree] = Printer.TreeStructure
//              report.errorAndAbort(s"Not annotated $annotTpe show=${annotTpe.fold(_.show, _.show)} show-dealias=${annotTpe.fold(_.show, _.dealias.show)} wtf=${annotTpe match {
//                  case Right(t @ AnnotatedType(_, term)) => term.show -> findTypeReprAnno(t, idAnnotationSym)
//                  case Right(t) => s"t=$t"
//                  case Left(t) => s"annotTpeRepr=${annotTpe._annotTpe} annotTpeReprShow=${annotTpe._annotTpe.show}"
//                }}")
        case (None, None) =>
          None
      }

      val tpeRepr = tpe._tpe

      val isByName = tpeRepr match {
        case ByNameType(_) => true
        case _ => false
      }

      val wasGeneric = tpeRepr.typeSymbol.isTypeParam // deem abstract type members as generic? No. Because we don't do that in Scala 2 version.

      '{
        LinkedParameter(
          SymbolInfo(
            name = ${ Expr(name) },
            finalResultType = ${ safeTypeFromRepr(tpeRepr) },
            isByName = ${ Expr(isByName) },
            wasGeneric = ${ Expr(wasGeneric) },
          ),
          ${ makeKeyFromRepr(tpeRepr, identifier) },
        )
      }
    }

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

    private def findSymbolAnno(sym: Symbol, annotationSym: Symbol): Option[Term] = {
      sym.getAnnotation(annotationSym)
    }

    private def findTypeAnno(t0: Either[TypeTree, TypeRepr], sym: Symbol): Option[Term] = {
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

}
