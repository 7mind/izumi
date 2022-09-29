package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}

import scala.annotation.experimental
import scala.quoted.{Expr, Quotes, Type}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.collection.immutable.Queue

object ClassConstructorMacro {

  object Experimental {
    @experimental
    def make[R: Type](using qctx: Quotes): Expr[ClassConstructor[R]] = try {
      import qctx.reflect.*

      val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()

      def wrapIntoLambda(paramss: List[List[(String, TypeTree)]], consTerm: Term) = {
        val params = paramss.flatten
        val mtpe = MethodType(params.map(_._1))(_ => params.map(_._2.tpe), _ => TypeRepr.of[R])
        val lam = Lambda(Symbol.spliceOwner, mtpe, {
          case (_, args0) =>
            val (_, argsLists) = paramss.foldLeft((args0, Queue.empty[List[Term]])){
              case ((args, res), params) =>
                val (argList, rest) = args.splitAt(params.size)
                (rest, res :+ (argList: List[Tree]).asInstanceOf[List[Term]])
            }

            val appl = argsLists.foldLeft(consTerm)(_.appliedToArgs(_))
            val trm = Typed(appl, TypeTree.of[R])
            trm
        })

//        report.warning(s"CLASSCONSTRUCTOR: $lam;;\n${lam.show}")

        val a = functoidMacro.make[R](lam.asExpr)

//        report.warning(s"CLASSCONSTRUCTOR: ${a.asTerm};;\n$a, ${a.asTerm.show}")

        '{ new ClassConstructor[R](${a}) }
      }

      Expr.summon[ValueOf[R]] match {
        case Some(valexpr) =>
          '{new ClassConstructor[R](Functoid.singleton(${valexpr.asExprOf[scala.Singleton & R]}))}
        case _ =>
          ConstructorUtil.requireConcreteTypeConstructor[R]("ClassConstructor")

          val typeRepr = TypeRepr.of[R].dealias.simplified

          typeRepr.classSymbol.map(cs => (cs, cs.primaryConstructor)).filterNot(_._2.isNoSymbol) match {
            case Some(cs, consSym) =>
//              report.warning(s"Constructor paramsyms: ${consSym.paramSymss} - tpes: (${consSym.paramSymss.map(_.map(s => cs.typeRef.memberType(s)))})")

              val methodTypeApplied = consSym.owner.typeRef.memberType(consSym).appliedTo(typeRepr.typeArgs)
              report.warning(s"- method type raw = ${typeRepr.memberType(consSym)}\n- methodType applied = $methodTypeApplied\n")
//              report.warning(s"- methodType applied paramsyms = ${methodTypeApplied.typeSymbol.paramSymss}\n - tpes: (${methodTypeApplied.typeSymbol.paramSymss.map(_.map(s => methodTypeApplied.memberType(s)))})")

              val argTypes = typeRepr match {
                case AppliedType(_, args) =>
                  args.map(repr => TypeTree.of(using repr.asType))
                case _ =>
                  Nil
              }

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

              val ctorTree = Select(New(TypeIdent(cs)), consSym)
              val ctorTreeParameterized = ctorTree.appliedToTypeTrees(argTypes)

              wrapIntoLambda(paramss, ctorTreeParameterized)

            case None =>
              report.errorAndAbort("NUTHING")
          }

      }
    } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

  }


}
