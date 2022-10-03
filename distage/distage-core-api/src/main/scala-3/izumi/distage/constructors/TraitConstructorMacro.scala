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

    val methodDecls = abstractMethods.map {
      m =>
        (m.symbol.name, m.returnTpt)
    }

    val constructorParams = if (isAbstract) {
      // TODO: decopypaste
      val consSym = sym.primaryConstructor
      val methodTypeApplied = consSym.owner.typeRef.memberType(consSym).appliedTo(tpe.typeArgs)

      val paramss: List[List[(String, TypeTree)]] = {
        def getParams(t: TypeRepr): List[List[(String, TypeTree)]] = {
          t match {
            case MethodType(paramNames, paramTpes, res) =>
              paramNames.zip(paramTpes.map(repr => TypeTree.of(using repr.asType))) :: getParams(res)
            case _ =>
              Nil
          }
        }

        getParams(methodTypeApplied)
      }

      paramss
    } else {
      List.empty
    }

    val lamParams = if (isAbstract) {
      // TODO:
      methodDecls ++ constructorParams.flatten
    }  else {
      methodDecls
    }

    val name: String = s"${sym.name}AutoImpl"




    def decls(cls: Symbol): List[Symbol] = methodDecls.map {
      case(name, tpt) =>
        val mtype = tpt.symbol.typeRef
        // for () methods: MethodType(Nil)(_ => Nil, _ => m.returnTpt.symbol.typeRef)
        Symbol.newMethod(cls,
          name,
          mtype,
          Flags.Method | Flags.Override,
          Symbol.noSymbol
        )
    }




    val lamExpr = ConstructorUtil.wrapIntoLambda[qctx.type, R](List(lamParams.map({case (n, t) => ("_"+n, t)}))) {
      args0 =>


        val parents0 = if (isAbstract) {
          List(tree)
        } else {
          List(TypeTree.of[Object], tree)
        }

        val parents = if (isAbstract) {
          // TODO
          val consSym = sym.primaryConstructor
          val ctorTree = Select(New(TypeIdent(tpe.classSymbol.get)), consSym)

          val argTypes = tpe match {
            case AppliedType(_, args) =>
              args.map(repr => TypeTree.of(using repr.asType))
            case _ =>
              Nil
          }
          val ctorTreeParameterized = ctorTree.appliedToTypeTrees(argTypes)

          val (_, argsLists) = constructorParams.foldLeft((args0.drop(methodDecls.size), Queue.empty[List[Term]])) {
            case ((args, res), params) =>
              val (argList, rest) = args.splitAt(params.size)
              (rest, res :+ (argList: List[Tree]).asInstanceOf[List[Term]])
          }


          val appl = argsLists.foldLeft(ctorTreeParameterized)(_.appliedToArgs(_))
          List(appl)
        } else {
          List(TypeTree.of[Object], tree)
        }

//        report.errorAndAbort(s"${parents.map(_.symbol.typeRef)}")
        val cls = Symbol.newClass(Symbol.spliceOwner, name, parents = parents0.map(_.tpe), (cls: Symbol) => decls(cls), selfType = None)

        val defs = methodDecls.zip(args0).map {
          case ((name, tpt), arg) =>
            val fooSym = cls.declaredMethod(name).head
            val t = tpt.symbol.typeRef

            DefDef (fooSym, argss => Some ('{${arg.asExprOf[Any]}}.asTerm) )

//            t.asType match {
//              case '[r] =>
//             DefDef (fooSym, argss => Some ('{${arg.asExprOf[Any]}.asInstanceOf[r]}.asTerm) )
//            }
        }

        val clsDef = ClassDef(cls, parents, body = defs)
        val newCls = Typed(Apply(Select(New(TypeIdent(cls)), cls.primaryConstructor), Nil), TypeTree.of[R])
        val block = Block(List(clsDef), newCls).asExprOf[R]
        val constructorTerm = block.asTerm
        Typed(constructorTerm, TypeTree.of[R])
    }

    report.warning(s"""|symbol = ${sym};
      |tree = ${sym.tree}
      |pct  = ${sym.primaryConstructor.tree}
      |pcs  = ${sym.primaryConstructor.tree.show}
      |defn = ${sym.tree.show}
      |lam  = ${lamExpr}
      |lam  = ${lamExpr.asTerm}
      |lam  = ${lamExpr.show}
      |""".stripMargin)

    val f = functoidMacro.make[R](lamExpr)
    '{new TraitConstructor[R](${f})}


    } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
