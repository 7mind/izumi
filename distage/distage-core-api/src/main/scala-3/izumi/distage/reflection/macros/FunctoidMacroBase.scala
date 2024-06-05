package izumi.distage.reflection.macros

import izumi.distage.model.definition.Id
import izumi.distage.model.providers.AbstractFunctoid
import izumi.distage.model.reflection.*
import izumi.fundamentals.platform.reflection.ReflectionUtil
import izumi.reflect.Tag

import scala.annotation.tailrec
import scala.collection.immutable.{List, Seq}
import scala.language.implicitConversions
import scala.quoted.{Expr, Quotes, Type}

trait FunctoidMacroBase[Ftoid[+K] <: AbstractFunctoid[K, Ftoid]] {
  protected def generateFunctoid[R: Type, Q <: Quotes](paramDefs: List[Expr[LinkedParameter]], originalFun: Expr[AnyRef])(using qctx: Q): Expr[Ftoid[R]]
  
  protected def generateSafeType[R: Type, Q <: Quotes](using qctx: Q): Expr[SafeType] = {
    new FunctoidParametersMacro[qctx.type]().safeType[R]
  }

  protected def generateRawFnCall[Q <: Quotes](argsCount: Int, rawFn: Expr[Any], args: Expr[Seq[Any]])(using qctx: Q): Expr[Any] = {
    import qctx.reflect.*

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

  final class FunctoidMacroImpl[Q <: Quotes](using val qctx: Q) {

    import qctx.reflect.*

    private val paramsMacro = new FunctoidParametersMacro[qctx.type]()

    def make[R: Type](fun: Expr[AnyRef]): Expr[Ftoid[R]] = {
      val parameters = analyze(fun.asTerm)
      val out = generateFunctoid[R, Q](parameters, fun)

      //      report.warning(
      //        s"""fun=${fun.show}
      //           |funType=${fun.asTerm.tpe}
      //           |funSym=${fun.asTerm.symbol}
      //           |funTypeSym=${fun.asTerm.tpe.typeSymbol}
      //           |funTypeSymBases=${fun.asTerm.tpe.baseClasses}
      //           |outputType=${Type.show[R]}
      //           |rawOutputType=(${TypeRepr.of[R]})
      //           |produced=${out.show}""".stripMargin
      //      )

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

    private def analyzeLambdaOrMethodRef(name: String, singleParamList: TermParamClause, body: Term): List[Expr[LinkedParameter]] = {
      val methodRefParams = {
        @tailrec
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
      val functionTpe = rawTpe.baseClasses.find(_.fullName.startsWith("scala.Function")) match {
        case Some(fn) =>
          rawTpe.baseType(fn)
        case None =>
          report.errorAndAbort(s"Could not find scala.Function* base class for ${rawTpe.show} - not a function! baseClasses were: ${rawTpe.baseClasses}")
      }

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

  }

  final class FunctoidParametersMacro[Q <: Quotes](using val qctx: Q) {

    import qctx.reflect.*

    private val idAnnotationSym: Symbol = TypeRepr.of[Id].typeSymbol
    private val maybeJavaxNamedAnnotationSym: Option[Symbol] = scala.util.Try(Symbol.requiredClass("javax.inject.Named")).toOption

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
      val identifier = {
        val mbIdIdentifier = ReflectionUtil
          .readTypeOrSymbolDIAnnotation(idAnnotationSym)(name, annotSym, annotTpe) {
            case aterm @ Apply(Select(New(_), _), c :: _) =>
              c.asExprOf[String].value.orElse {
                report.errorAndAbort(s"distage.Id annotation expects one literal String argument but got ${c.show} in tree ${aterm.show} ($aterm)")
              }
            case aterm =>
              report.errorAndAbort(s"distage.Id annotation expects one literal String argument but got malformed tree ${aterm.show} ($aterm)")
          }
        mbIdIdentifier.orElse {
          maybeJavaxNamedAnnotationSym.flatMap {
            namedAnnoSym =>
              ReflectionUtil.readTypeOrSymbolDIAnnotation(namedAnnoSym)(name, annotSym, annotTpe) {
                case aterm @ Apply(Select(New(_), _), c :: _) =>
                  c.asExprOf[String].value.orElse {
                    report.errorAndAbort(s"javax.inject.Named annotation expects one literal String argument but got ${c.show} in tree ${aterm.show} ($aterm)")
                  }
                case aterm =>
                  report.errorAndAbort(s"javax.inject.Named annotation expects one literal String argument but got malformed tree ${aterm.show} ($aterm)")
              }
          }
        }
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
          '{ new DIKey.TypeKey($safeTpe, None) }
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

  }

}
