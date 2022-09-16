package izumi.distage.model.providers

import izumi.distage.model.reflection.Provider.{ProviderImpl, ProviderType}
import izumi.distage.model.reflection.{DIKey, LinkedParameter, SafeType, SymbolInfo}
import izumi.reflect.Tag
import izumi.distage.model.definition.Id

import scala.annotation.{experimental, tailrec}
import scala.collection.immutable.List
import scala.language.implicitConversions

trait FunctoidMacroMethods {
  import FunctoidMacro.*

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
  // TODO: FunctionXXL / https://docs.scala-lang.org/scala3/reference/dropped-features/limit22.html
}


object FunctoidMacro {
  import scala.quoted.{Expr, Quotes, Type}

  inline def make[R](inline fun: Any): Functoid[R] = ${ Experimental.make[R]('fun) }

  object Experimental {
    @experimental
    def make[R: Type](fun: Expr[Any])(using qctx: Quotes): Expr[Functoid[R]] = new CodePositionMaterializerMacro().make(fun)
  }

  @experimental
  private final class CodePositionMaterializerMacro(using val qctx: Quotes) {

    import qctx.reflect.*

    def make[R: Type](fun: Expr[Any]): Expr[Functoid[R]] = {
      val out = matchTerm[R](fun, fun.asTerm)
      report.warning(s"${fun.show} produced ${out.show}")
      out
    }

    @tailrec
    def matchTerm[R : Type](srcdef: Expr[Any], fdef: Term): Expr[Functoid[R]] = {
      fdef match {
        case Block(List(DefDef(name, params :: Nil, _, _)), Closure(_, _)) =>
          val paramTypes = params.params.map {
            case ValDef(name, tpe, _) =>
              (name, tpe)
            case p =>
              report.errorAndAbort(s"Unexpected parameter ${p.show}")
          }
          analyzeParams[R](paramTypes, fdef)

        case Typed(term, _) =>
          matchTerm(srcdef, term)
        case Inlined(_, _, term) =>
          matchTerm(srcdef, term)
        case Block(_, term) =>
          matchTerm(srcdef, term)
        case expr =>
          val allTParams = expr.underlying.tpe.typeArgs.map(a => TypeTree.of(using a.asType))
          val args = allTParams match {
            case Nil => Nil
            case o => o.init
          }
          analyzeParams[R](args.zipWithIndex.map(a => (s"arg_${a._2}", a._1)), fdef)
      }
    }

    def analyzeParams[R : Type](params: List[(String, TypeTree)], fdef: Term) = {
      val paramTypes = params.map(_._2)

      val paramDefs = params.map {
        case (name, tpe) =>
          val identifier = tpe match {
            case Annotated(_, aterm) =>
              aterm.asExprOf[Any] match {
                case '{ new Id($c) } =>
                  c.asTerm match {
                    case Literal(v) =>
                      Some(v.value.toString)
                    case _ =>
                      report.errorAndAbort (s"distage.Id annotation expects one literal argument but got ${c.show} in tree ${aterm.show}")
                  }
                case _ =>
                  None
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
              name = $ {
                Expr(name)
              },
              finalResultType = ${safeType(tpe)},
              isByName = ${ Expr(isByName) },
              wasGeneric = false, // TODO:
            ),
            ${
              makeKey(tpe, identifier)
            }
          )
          }
      }


      '{
        val rawFn: AnyRef = ${fdef.asExprOf[AnyRef]}
        new Functoid[R](
          new ProviderImpl[R](
            $ {Expr.ofList(paramDefs)},
            ${safeType[R]},
            rawFn,
            (args: Seq[Any]) => $ {generateCall(paramTypes, 'rawFn, 'args)},
            ProviderType.Function,
          )
        )
      }
    }

    def generateCall(ptypes: List[TypeTree], fn: Expr[Any], arg: Expr[Seq[Any]]): Expr[Any] = {
      val params = ptypes.zipWithIndex.map{
        case (_, idx) =>
          '{ $arg( ${Expr(idx)} ) }
      }
      val argTypes = (0 to ptypes.size).map(_ => TypeRepr.of[Any]).toList

      val tref = defn.FunctionClass(ptypes.size).typeRef.appliedTo(argTypes)

      val fnAny = tref.asType match {
        case '[a] =>
          '{ ${fn.asExprOf[Any]}.asInstanceOf[a] }
        case _ =>
          report.errorAndAbort(s"This is totally unexpected: ${tref.show} didn't match where it had to")
      }

      Select.unique(fnAny.asTerm, "apply").appliedToArgs(params.map(_.asTerm).toList).asExprOf[Any]
    }

    def safeType[R:Type]: Expr[SafeType] = {
      '{ SafeType.get[R](scala.compiletime.summonInline[Tag[R]]) }
    }

    def safeType(tpe: TypeTree): Expr[SafeType] = {
      tpe.tpe match {
        case ByNameType(u) =>
          safeTypeFromRepr(u)
        case o =>
          safeTypeFromRepr(o)
      }
    }

    def safeTypeFromRepr(tpe: TypeRepr): Expr[SafeType] = {
        tpe.asType match {
          case '[a] => '{ SafeType.get[a](using scala.compiletime.summonInline[Tag[a]] ) }
          case o =>
            report.errorAndAbort(s"Cannot generate SafeType from ${tpe.show}, probably that's a bug in Functoid macro")
        }
    }

    def makeKey(tpe: TypeTree, id: Option[String]): Expr[DIKey] = {
      tpe.tpe match {
        case ByNameType(u) =>
          makeKeyfromRepr(u, id)
        case o =>
          makeKeyfromRepr(o, id)
      }
    }

    def makeKeyfromRepr(tpe: TypeRepr, id: Option[String]): Expr[DIKey] = {
      tpe.asType match {
        case '[a] =>
          id match {
            case Some(s) =>
              '{ DIKey.apply[a](${ Expr(s) })(scala.compiletime.summonInline[Tag[a]]) }
            case None =>
              '{ DIKey.apply[a]((scala.compiletime.summonInline[Tag[a]])) }
          }
        case _ =>
          report.errorAndAbort(s"Cannot generate DIKey from ${tpe.show}, probably that's a bug in Functoid macro")
      }
    }
  }

}