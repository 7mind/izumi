package izumi.distage.model.providers

import izumi.distage.model.reflection.Provider.{ProviderImpl, ProviderType}
import izumi.distage.model.reflection.{DIKey, LinkedParameter, SafeType, SymbolInfo}
import izumi.reflect.Tag

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
}


object FunctoidMacro {
  import scala.quoted.{Expr, Quotes, Type}

  inline def make[R](inline fun: Any): Functoid[R] = ${ X.make[R]('fun) }

  object X {
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
        case Block(List(DefDef(name, params :: Nil, ret, _)), Closure(_, _)) =>
          val paramTypes = params.params.map {
            case ValDef(name, tpe, _) =>
              (name, tpe)
            case p =>
              report.errorAndAbort(s"Unexpected parameter ${p.show}")
          }
          analyzeParams[R](paramTypes, name, ret, fdef)

        case Typed(term, _) =>
          matchTerm(srcdef, term)
        case Inlined(_, _, term) =>
          matchTerm(srcdef, term)
        case Block(_, term) =>
          matchTerm(srcdef, term)
        case idt@Ident(s) =>
          val allTParams = idt.underlying.tpe.typeArgs.map(a => TypeTree.of(using a.asType))
          val args = allTParams.init
          val ret = allTParams.last
          analyzeParams[R](args.zipWithIndex.map(a => (s"arg_${a._2}", a._1)), s, ret, fdef)

        case _ =>
          report.errorAndAbort(s"${System.nanoTime()}: failed to process: ${srcdef.show}; ${srcdef.asTerm}")
      }
    }

    def analyzeParams[R : Type](params: List[(String, TypeTree)], clue: String, ret: TypeTree, fdef: Term) = {
      val paramTypes = params.map(_._2)

      val paramDefs = params.map {
        case (name, tpe) =>
          val tag = findTag(name, tpe)
          val annos = tpe match {
            case Annotated(_, aterm) =>
            //report.warning((aterm.show, aterm).toString)

            case _ =>
              List.empty
          }

          '{
          LinkedParameter(
            SymbolInfo(
              name = $ {
                Expr(name)
              },
              finalResultType = SafeType.fromTag($tag),
              isByName = false, // TODO:
              wasGeneric = false,
            ),
            ${
              makeKey(tpe, None)
            }
          )
          }
      }

      val rett = findTag(clue, ret)

      val out = '{
      val rawFn: AnyRef = ${fdef.asExprOf[AnyRef]}
      new Functoid[R](
        new ProviderImpl[R](
          $ {Expr.ofList(paramDefs)},
          SafeType.fromTag($rett),
          rawFn,
          (args: Seq[Any]) => $ {test(paramTypes, 'rawFn, 'args)},
          ProviderType.Function,
        )
      )
      }
      out
    }

    def test(ptypes: List[TypeTree], fn: Expr[Any], arg: Expr[Seq[Any]]): Expr[Any] = {
      val params = ptypes.zipWithIndex.map{
        case (_, idx) =>
          '{ $arg( ${Expr(idx)} ) }

//          ptype.tpe.asType match {
//            case '[a] =>
//              '{ $arg( ${Expr(idx)} ).asInstanceOf[a] }
//          }
      }
      val argTypes = (0 to ptypes.size).map(_ => TypeRepr.of[Any]).toList

      val tref = defn.FunctionClass(ptypes.size).typeRef.appliedTo(argTypes)

      val fnAny = tref.asType match {
        case '[a] =>
          '{ ${fn.asExprOf[Any]}.asInstanceOf[a] }
      }

      Select.unique(fnAny.asTerm, "apply").appliedToArgs(params.map(_.asTerm).toList).asExprOf[Any]
    }

    def findTag(clue: String, tpe: TypeTree): Expr[Tag[Any]] = {
      tpe.tpe.asType match {
        case '[a] =>
          Expr.summon[Tag[a]].asInstanceOf[Option[Expr[Tag[Any]]]] match {
            case Some(c) =>
              '{ ${c}.asInstanceOf[Tag[Any]] }
              //'{ Some($ ) }
            case None =>
              report.errorAndAbort (s"Can't find type tag for $clue: ${tpe.show}")
          }
      }
    }

    def makeKey(tpe: TypeTree, id: Option[String]): Expr[DIKey] = {
      tpe.tpe.asType match {
        case '[a] =>
          id match {
            case Some(s) => '{ DIKey.apply[a](${ Expr(s) }) }
            case None =>          '{ DIKey.apply[a] }
          }
      }
    }

  }
}