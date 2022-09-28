package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}

import scala.annotation.experimental


object ClassConstructorMacro {
  import scala.quoted.{Expr, Quotes, Type}

  object Experimental {
    @experimental
    def make[R: Type](using qctx: Quotes): Expr[ClassConstructor[R]] = {
      import qctx.reflect.*

      val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()

      def wrapIntoLambda(paramTypes: List[(String, TypeTree)], consTerm: Term) = {
        val mtpe = MethodType(paramTypes.map(_._1))(_ => paramTypes.map(_._2.tpe), _ => TypeRepr.of[R])
        val lam = Lambda(Symbol.spliceOwner, mtpe, {
            case (methSym, args) =>
              // paramTypes.map(t => Ident(TermRef(TypeTree.ref(Symbol.noSymbol).tpe, t._1))
              val appl = consTerm.appliedToArgs(args.map(_.asExpr.asTerm))
              val newCls = Typed(appl, TypeTree.of[R])
              val trm = Block(List.empty, newCls)
              trm
        })

//        report.errorAndAbort(s"CLASSCONSTRUCTOR: ${lam};; ${lam.show}")

        val a = functoidMacro.make[R](lam.asExpr)

//        report.warning(s"CLASSCONSTRUCTOR: ${a.asTerm};; $a, ${a.asTerm.show}")

        '{new ClassConstructor[R](${a})}
      }

      Expr.summon[ValueOf[R]] match {
        case Some(valexpr) =>
          '{new ClassConstructor[R](Functoid.singleton(${valexpr.asExprOf[scala.Singleton & R]}))}
        case _ =>
          ConstructorUtil.requireConcreteTypeConstructor[R]("ClassConstructor")

          val typeRepr = TypeRepr.of[R].dealias.simplified

          typeRepr.classSymbol.map(cs => (cs, cs.primaryConstructor)).filterNot(_._2.isNoSymbol) match {
            case Some(cs, consSym) =>
              consSym.tree match {
                case DefDef(_, List(paramClauses), _, _) =>

                  val paramTypes = paramClauses.params.map {
                    case ValDef(name, tpe, _) =>
                      (name, tpe)
                    case p =>
                      report.errorAndAbort(s"Unexpected parameter: ${p.show}")
                  }

                  wrapIntoLambda(paramTypes, Select(New(TypeIdent(cs)), consSym))


                case DefDef(_, manyClauses, _, _) =>
                  val argTypes = typeRepr match {
                    case AppliedType(_, args) =>
                      args.map(repr => TypeTree.of(using repr.asType))
                    case _ =>
                      ???
                  }
                  val dd = manyClauses.head.params.map {
                    case TypeDef(name, _) =>
                      name
                    case _ =>
                      ???
                  }
                  assert(dd.size == argTypes.size)
                  val types = dd.zip(argTypes).toMap

                  val paramTypes = manyClauses.last.params.map {
                    case ValDef(name, tpe, _) =>
                      (name, types.getOrElse(tpe.symbol.name, tpe))
                    case p =>
                      report.errorAndAbort(s"Unexpected parameter: ${p.show}")
                  }
                  
                  wrapIntoLambda(paramTypes, Select(New(TypeTree.of(using typeRepr.asType)), consSym).appliedToTypeTrees(paramTypes.map(_._2)))
                  
                case _ =>
                  report.errorAndAbort(s"NUTHING2: ${typeRepr};; ${consSym};; ${consSym.tree}")
              }


            case None =>
              report.errorAndAbort("NUTHING")

          }

      }
    }

  }


}
