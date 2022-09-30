package izumi.distage.constructors

import izumi.distage.model.providers.{Functoid, FunctoidMacro}

import scala.quoted.{Expr, Quotes, Type}
import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable

import scala.collection.immutable.Queue

object ClassConstructorMacro {

  def make[R: Type](using qctx: Quotes): Expr[ClassConstructor[R]] = try {
      import qctx.reflect.*

      val functoidMacro = new FunctoidMacro.FunctoidMacroImpl[qctx.type]()



      Expr.summon[ValueOf[R]] match {
        case Some(valexpr) =>
          '{new ClassConstructor[R](Functoid.singleton(${valexpr.asExprOf[scala.Singleton & R]}))}
        case _ =>
          ConstructorUtil.requireConcreteTypeConstructor[R]("ClassConstructor")

          val typeRepr = TypeRepr.of[R].dealias.simplified

          typeRepr.classSymbol.map(cs => (cs, cs.primaryConstructor)).filterNot(_._2.isNoSymbol) match {
            case Some(cs, consSym) =>
              val methodTypeApplied = consSym.owner.typeRef.memberType(consSym).appliedTo(typeRepr.typeArgs)

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

              val lamExpr = ConstructorUtil.wrapApplicationIntoLambda[qctx.type, R](paramss, ctorTreeParameterized)
              val f = functoidMacro.make[R](lamExpr)
              '{new ClassConstructor[R](${f})}
            case None =>
              report.errorAndAbort(s"Cannot find primary constructor in ${typeRepr}")
          }

      }
    } catch { case t: Throwable => qctx.reflect.report.errorAndAbort(t.stackTrace) }

}
