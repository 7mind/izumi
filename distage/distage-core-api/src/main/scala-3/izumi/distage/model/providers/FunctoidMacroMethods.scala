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

    val idAnnotationSym = TypeRepr.of[Id].typeSymbol

    def makeParams[R: Type](params: List[(String, TypeTree)]): (List[Expr[LinkedParameter]], List[TypeTree]) = {
      val paramTypes = params.map(_._2)

      val paramDefs = params.map {
        case (name, tpe) =>
          val identifier = tpe match {
            case Annotated(_, aterm) if aterm.tpe.baseClasses.contains(idAnnotationSym) =>
              aterm match {
                case Apply(Select(New(_), _), c :: _) =>
                  c.asExprOf[String].value.orElse {
                    report.errorAndAbort(s"distage.Id annotation expects one literal argument but got ${c.show} in tree ${aterm.show} ($aterm)")
                  }
                case _ =>
                  report.errorAndAbort(s"distage.Id annotation expects one literal argument but got malformed tree ${aterm.show} ($aterm)")
              }
            case _ =>
              None
          }

          val isByName = tpe.tpe match {
            case ByNameType(_) => true
            case _ => false
          }

          '{
            LinkedParameter(
              SymbolInfo(
                name = ${
                  Expr(name)
                },
                finalResultType = ${ safeType(tpe) },
                isByName = ${ Expr(isByName) },
                wasGeneric = ${ Expr(tpe.tpe.typeSymbol.isTypeParam) }, // TODO: type members?
              ),
              ${ makeKey(tpe, identifier) },
            )
          }
      }

      (paramDefs, paramTypes)
    }

    def makeKey(tpe: TypeTree, id: Option[String]): Expr[DIKey] = {
      tpe.tpe match {
        case ByNameType(u) =>
          makeKeyfromRepr(u, id)
        case o =>
          makeKeyfromRepr(o, id)
      }
    }

    private def makeKeyfromRepr(tpe: TypeRepr, id: Option[String]): Expr[DIKey] = {
      val safeTypeT = safeTypeFromRepr(tpe)
      id match {
        case Some(str) =>
          val strExpr = Expr(str)
          '{ new DIKey.IdKey($safeTypeT, $strExpr, None)(scala.compiletime.summonInline[IdContract[String]]) }
        case None =>
          '{ new DIKey.TypeKey($safeTypeT) }
      }
    }

    def safeType[R: Type]: Expr[SafeType] = {
      '{ SafeType.get[R](scala.compiletime.summonInline[Tag[R]]) }
    }

    private def safeType(tpe: TypeTree): Expr[SafeType] = {
      tpe.tpe match {
        case ByNameType(u) =>
          safeTypeFromRepr(u)
        case o =>
          safeTypeFromRepr(o)
      }
    }

    private def safeTypeFromRepr(tpe: TypeRepr): Expr[SafeType] = {
      tpe.asType match {
        case '[a] =>
          '{ SafeType.get[a](using scala.compiletime.summonInline[Tag[a]]) }
        case _ =>
          report.errorAndAbort(s"Cannot generate SafeType from ${tpe.show}, probably that's a bug in Functoid macro")
      }
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
          val allTParams = fun.underlying.tpe.typeArgs.map(a => TypeTree.of(using a.asType))
          val args = allTParams match {
            case Nil => Nil
            case o => o.init
          }
          analyzeParams[R](args.zipWithIndex.map(a => (s"arg_${a._2}", a._1)), fun)
      }
    }

    def matchStatement[R: Type](statement: Statement, fun: Term): Expr[Functoid[R]] = {
      statement match {
        case DefDef(name, params :: Nil, _, _) =>
          val paramTypes = params.params.map {
            case ValDef(name, tpe, _) =>
              (name, tpe)
            case p =>
              report.errorAndAbort(s"Unexpected parameter in $name: ${p.show}")
          }
          analyzeParams[R](paramTypes, fun)
      }
    }

    def analyzeParams[R: Type](params: List[(String, TypeTree)], fun: Term): Expr[Functoid[R]] = {
      val (paramDefs, paramTypes) = paramsMacro.makeParams[R](params)

      '{
        val rawFn: AnyRef = ${ fun.asExprOf[AnyRef] }
        new Functoid[R](
          new ProviderImpl[R](
            ${ Expr.ofList(paramDefs) },
            ${ paramsMacro.safeType[R] },
            rawFn,
            (args: Seq[Any]) => ${ generateCall(paramTypes, 'rawFn, 'args) },
            ProviderType.Function,
          )
        )
      }
    }

    def generateCall(ptypes: List[TypeTree], rawFn: Expr[Any], args: Expr[Seq[Any]]): Expr[Any] = {
      val params = ptypes.zipWithIndex.map {
        case (_, idx) =>
          '{ $args(${ Expr(idx) }) }
      }

      val fnType = defn.FunctionClass(ptypes.size).typeRef.appliedTo((0 to ptypes.size).map(_ => TypeRepr.of[Any]).toList)

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
