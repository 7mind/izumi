package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.annotation.experimental
import scala.collection.immutable.{ArraySeq, Queue}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

object FactoryConstructorMacro {

  @experimental
  def make[R: Type](using qctx: Quotes): Expr[FactoryConstructor[R]] = try {
    import qctx.reflect.*

    val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()
    val util = new ConstructorUtil[qctx.type]()
    import util.ParamListExt

    val resultTpe = TypeRepr.of[R].dealias.simplified
    val resultTypeTree = TypeTree.of[R]
    val resultTpeSym = resultTpe.typeSymbol

    val refinementMethods = util.unpackRefinement(resultTpe)

    val abstractMethods = resultTpeSym.methodMembers
      .filter(m => m.flags.is(Flags.Method) && m.flags.is(Flags.Deferred) && !m.flags.is(Flags.Artifact) && m.isDefDef)

    if (abstractMethods.isEmpty && refinementMethods.isEmpty) {
      report.errorAndAbort(
        s"""$resultTpeSym has no abstract methods so it's not a factory;; ${resultTpeSym.methodMembers};; $resultTypeTree;; ${resultTypeTree.getClass}""".stripMargin
      )
    }

    // TODO: handle refinements

    val parentsSymbols = util.findRequiredImplParents(resultTpeSym)
    val constructorParamLists = parentsSymbols.map(util.buildConstructorParameters(resultTpe))
    val flatCtorParams = constructorParamLists.flatMap(_._2.iterator.flatten)

    val methodDecls = abstractMethods.map(m => (m.name, m.owner.typeRef.memberType(m))) ++ refinementMethods
    val refinementNames = refinementMethods.map(_._1).toSet

    def decls(cls: Symbol): List[Symbol] = methodDecls.map {
      case (name, mtype) =>
        // for () methods MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef) instead of mtype
        val flags = if (!refinementNames.contains(name)) {
          Flags.Method | Flags.Override
        } else {
          Flags.Method
        }
        Symbol.newMethod(cls, name, mtype, flags, Symbol.noSymbol)
    }

    val lamParams = {
      val ctorArgs = flatCtorParams.map((n, t) => (n, util.normalizeType(t)))

      val byNameMethodArgs = methodDecls.flatMap {
        (n, t) =>
          val rett = util.normalizeType(t) // TODO: handle @With
          val constructorParamLists = util.buildConstructorParameters(rett)(rett.typeSymbol)
          val flatCtorParams = constructorParamLists._2.flatten // TODO: subtract signature parameters

          flatCtorParams.map {
            case (pn, pt) =>
              (s"_${n}_$pn", util.ensureByName(pt))
          }
      }

      (ctorArgs ++ byNameMethodArgs).toTrees
    }

    val lamExpr = util.wrapIntoLambda[R](List(lamParams)) {
      (lamSym, args0) =>

        val (lamOnlyCtorArguments, lamOnlyMethodArguments) = args0.splitAt(flatCtorParams.size)

        val parents = util.buildParentConstructorCallTerms(resultTpe, constructorParamLists, lamOnlyCtorArguments)

        val name: String = s"${resultTpeSym.name}FactoryAutoImpl"
        val clsSym = Symbol.newClass(lamSym, name, parents = parentsSymbols.map(_.typeRef), decls = decls, selfType = None)

        val defs = methodDecls.zip(lamOnlyMethodArguments).map {
          case ((name, _), arg) =>
            val fooSym = clsSym.declaredMethod(name).head

            // TODO: generate impl
            DefDef(fooSym, _ => Some('{ ??? }.asTerm))
        }

        val clsDef = ClassDef(clsSym, parents.toList, body = defs)
        val newCls = Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), resultTypeTree)
        val block = Block(List(clsDef), newCls)
        Typed(block, resultTypeTree)
    }

    report.warning(
      s"""|symbol = $resultTpeSym, flags=${resultTpeSym.flags.show}
          |methods = ${resultTpeSym.methodMembers.map(s => s"name: ${s.name} flags ${s.flags.show}")}
          |tree = ${resultTpeSym.tree}
          |pcs  = ${resultTpeSym.primaryConstructor.tree.show}
          |pct  = ${resultTpeSym.primaryConstructor.tree}
          |pct-flags = ${resultTpeSym.primaryConstructor.flags.show}
          |pctt = ${resultTpeSym.typeRef.memberType(resultTpeSym.primaryConstructor)}
          |pcts = ${resultTpeSym.typeRef.baseClasses
           .map(s => (s, s.primaryConstructor)).map((cs, s) => if (s != Symbol.noSymbol) (cs, cs.flags.show, s.tree) else (cs, cs.flags.show, None))
           .mkString("\n")}
          |defn = ${resultTpeSym.tree.show}
          |lam  = ${lamExpr.asTerm}
          |lam  = ${lamExpr.show}
          |prms = ${methodDecls.map((n, t) => (s"_$n", t, t.getClass))}
          |""".stripMargin
    )

    val f = functoidMacro.make[R](lamExpr)
    '{ new FactoryConstructor[R](${ f }) }

  } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
