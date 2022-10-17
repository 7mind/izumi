package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.annotation.experimental
import scala.collection.immutable.{ArraySeq, Queue}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}

object TraitConstructorMacro {

  @experimental
  def make[R: Type](using qctx: Quotes): Expr[TraitConstructor[R]] = try {
    import qctx.reflect.*

    val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()

    val util = new ConstructorUtil[qctx.type]()
    import util.ParamListExt

    val context = new ConstructorContext[R, qctx.type](util)
    import context.*

    if (!context.isWireableTrait) {
      report.errorAndAbort(
        s"""$resultTpeSym has abstract methods taking parameters, expected only parameterless abstract methods:
           |  ${abstractMethodsWithParams.map(s => s.name -> s.flags.show)}
           |  [others: ${abstractMethods.map(s => s.name -> s.flags.show)}]""".stripMargin
      )
    }

    assert(methodDecls.map(_._1).size == methodDecls.size, "BUG: duplicated abstract method names")

    def decls(cls: Symbol): List[Symbol] = methodDecls.map {
      case (name, mtype) =>
        // for () methods MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef) instead of mtype
        Symbol.newMethod(cls, name, mtype, Flags.Method | Flags.Override, Symbol.noSymbol)
    }

    // TODO: decopypaste
    val lamParams = {
      val ctorArgs = flatCtorParams.map((n, t) => (n, util.dropMethodType(t)))
      val byNameMethodArgs = methodDecls.map((n, t) => (s"_$n", util.ensureByName(util.dropWrappers(t))))
      (ctorArgs ++ byNameMethodArgs).toTrees
    }

    val lamExpr = util.wrapIntoLambda[R](List(lamParams)) {
      (lamSym, args0) =>

        val (lamOnlyCtorArguments, lamOnlyMethodArguments) = args0.splitAt(flatCtorParams.size)

        val parents = util.buildParentConstructorCallTerms(resultTpe, constructorParamLists, lamOnlyCtorArguments)

        val name: String = s"${resultTpeSym.name}TraitAutoImpl"
        val clsSym = Symbol.newClass(lamSym, name, parents = parentsSymbols.map(_.typeRef), decls = decls, selfType = None)

        val defs = methodDecls.zip(lamOnlyMethodArguments).map {
          case ((name, _), arg) =>
            val methodSyms = clsSym.declaredMethod(name)
            assert(methodSyms.size == 1, "BUG: duplicated methods!")
            val methodSym = methodSyms.head
            DefDef(methodSym, _ => Some(arg))
        }

        val clsDef = ClassDef(clsSym, parents.toList, body = defs)
        val newCls = Typed(Apply(Select(New(TypeIdent(clsSym)), clsSym.primaryConstructor), Nil), resultTpeTree)
        val block = Block(List(clsDef), newCls)
        Typed(block, resultTpeTree)
    }

    /*
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
          |""".stripMargin
    )
     */

    val f = functoidMacro.make[R](lamExpr)
    '{ new TraitConstructor[R](${ f }) }

  } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
