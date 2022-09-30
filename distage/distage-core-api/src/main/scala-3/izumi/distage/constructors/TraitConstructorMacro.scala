package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.annotation.experimental
import scala.collection.immutable.Queue
import scala.quoted.{Expr, Quotes, Type}

object TraitConstructorMacro {

  @experimental
  def make[R: Type](using qctx: Quotes): Expr[TraitConstructor[R]] = try {
    import qctx.reflect.*

    val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()

    val tpe = TypeRepr.of[R].dealias.simplified
    val tree = TypeTree.of[R]

    val sym = tpe.typeSymbol
    val ms = sym.methodMembers
    val isTrait = sym.flags.is(Flags.Trait)
    val isAbstract = sym.flags.is(Flags.Abstract)

//    if (!isTrait && !isAbstract) {
//      report.errorAndAbort(s"$sym is not an abstract class or a trait")
//    }

    val abstractMethods = ms
      .filter(m => m.flags.is(Flags.Method) && m.flags.is(Flags.Deferred) && m.isDefDef)
      .map(_.tree.asInstanceOf[DefDef])

    val withParams = abstractMethods.filter(_.paramss.nonEmpty)

    if (withParams.nonEmpty) {
      report.errorAndAbort(s"$sym has abstract methods taking parameters: ${withParams.map(_.symbol.name)}")
    }


    val name: String = s"${sym.name}AutoImpl"

    val parents = if (isAbstract){
      List(tree)
    } else {
      List(TypeTree.of[Object], tree)
    }


    def decls(cls: Symbol): List[Symbol] = abstractMethods.map {
      m =>
        val mtype = m.returnTpt.symbol.typeRef
        // for () methods: MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef)
        Symbol.newMethod(cls,
          m.symbol.name,
          mtype,
          Flags.Method | Flags.Override,
          Symbol.noSymbol
        )
    }

    val cls = Symbol.newClass(Symbol.spliceOwner, name, parents = parents.map(_.tpe), (cls: Symbol) => decls(cls), selfType = None)

    val lamParams = abstractMethods.map {
      m =>
        (m.symbol.name, m.returnTpt)
    }

    val lamExpr = ConstructorUtil.wrapIntoLambda[qctx.type, R](List(lamParams)) {
      args0 =>
        val defs = abstractMethods.zip(args0).map {
          case (m, arg) =>
            val fooSym = cls.declaredMethod(m.symbol.name).head
            val t = m.returnTpt.symbol.typeRef

            t.asType match {
              case '[r] =>
                DefDef(fooSym, argss => Some('{${arg.asExprOf[Any]}.asInstanceOf[r]}.asTerm))
            }
        }

        val clsDef = ClassDef(cls, parents, body = defs)
        val newCls = Typed(Apply(Select(New(TypeIdent(cls)), cls.primaryConstructor), Nil), TypeTree.of[R])
        val block = Block(List(clsDef), newCls).asExprOf[R]
        val constructorTerm = block.asTerm
        Typed(constructorTerm, TypeTree.of[R])
    }

//    report.warning(s"""|symbol = ${sym};
//      |tree = ${sym.tree}
//      |pct  = ${sym.primaryConstructor.tree}
//      |pcs  = ${sym.primaryConstructor.tree.show}
//      |defn = ${sym.tree.show}
//      |lam  = ${lamExpr}
//      |lam  = ${lamExpr.show}
//      |""".stripMargin)

    val f = functoidMacro.make[R](lamExpr)
    '{new TraitConstructor[R](${f})}


    } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
