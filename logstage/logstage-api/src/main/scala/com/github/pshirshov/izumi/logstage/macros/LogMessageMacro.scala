package com.github.pshirshov.izumi.logstage.macros

import com.github.pshirshov.izumi.logstage.api.Log.{LogArg, Message}

import scala.collection.mutable
import scala.reflect.macros.blackbox

object LogMessageMacro {


  def logMessageMacro(c: blackbox.Context)(message: c.Expr[String]): c.Expr[Message] = {
    import c.universe._

    message.tree match {
      case Typed(tree, _) =>
        processExpr(c)(tree)
      case tree =>
        processExpr(c)(tree)
    }
  }

  private[this] def reifyContext(c: blackbox.Context)(stringContext: c.universe.Tree, namedArgs: c.Expr[List[LogArg]]): c.Expr[Message] = {
    import c.universe._
    reify {
      Message(
        c.Expr[StringContext](stringContext).splice
        , namedArgs.splice
      )
    }
  }

  private[this] def processExpr(c: blackbox.Context)(message: c.Tree): c.Expr[Message] = {
    import c.universe._

    sealed trait Part
    object Part {

      case class Element(v: c.Tree) extends Part

      case class Argument(v: c.Tree) extends Part

    }

    case class Out(parts: Seq[Part]) {
      def arguments: Seq[c.Tree] = {
        balanced.collect { case Part.Argument(expr) => expr }
      }

      def makeStringContext: c.Tree = {
        val elements = balanced.collect { case Part.Element(lit) => lit }
        val listExpr = c.Expr[Seq[String]](q"Seq(..$elements)")

        val scParts = reify {
          listExpr.splice
        }
        val sc = q"StringContext($scParts :_*)"
        sc
      }

      // we need to build StringContext compatible list of chunks and arguments where elements of the same type cannot occur consequently
      private lazy val balanced = {
        val balancedParts = mutable.ArrayBuffer[Part]()

        val emptystring = Part.Element(Literal(Constant("")))

        parts.foreach {
          case e: Part.Element =>
            balancedParts.lastOption match {
              case Some(value: Part.Element) =>
                balancedParts.append(Part.Element(q"${value.v} + ${e.v}"))
              case _ =>
                balancedParts.append(e)
            }

          case a: Part.Argument =>
            balancedParts.lastOption match {
              case Some(_: Part.Argument) =>
                balancedParts.append(emptystring)
              case None =>
                balancedParts.append(emptystring)
              case _ =>
            }

            balancedParts.append(a)
        }

        balancedParts.lastOption match {
          case Some(_: Part.Argument) =>
            balancedParts.append(emptystring)
          case _ =>
        }
        balancedParts.toSeq
      }
    }

    object PlusExtractor {
      def unapply(arg: c.universe.Tree): Option[Out] = {
        arg match {
          case Apply(Select(applyselect, TermName("$plus")), args: List[c.Tree]) =>
            val sub = applyselect match {
              case PlusExtractor(out) =>
                out

              case const@Literal(Constant(_)) =>
                Out(Seq(Part.Element(const)))

              case o =>
                Out(Seq(Part.Argument(o)))
            }

            // in sequence of string concatenations ("" + "" + "") we expect only one argument to be at the right side
            args match {
              case head :: Nil =>
                head match {
                  case t@Literal(Constant(_)) =>
                    Some(sub.copy(parts = sub.parts :+ Part.Element(t)))
                  case argexpr =>
                    Some(sub.copy(parts = sub.parts :+ Part.Argument(argexpr)))
                }

              case _ =>
                c.warning(c.enclosingPosition, s"Something is wrong with this expression, please report this as a bug: $applyselect, ${c.universe.showRaw(applyselect)}")
                Some(sub)
            }

          case _ => None
        }
      }
    }

    message match {
      case PlusExtractor(lst) =>
        val namedArgs = ArgumentNameExtractionMacro.recoverArgNames(c)(lst.arguments.map(p => c.Expr(p)))
        val sc = lst.makeStringContext
        reifyContext(c)(sc, namedArgs)

      case Apply(Select(stringContext@Apply(Select(Select(Ident(TermName("scala")), TermName("StringContext")), TermName("apply")), _), TermName("s")), args: List[c.Tree]) =>
        // qq causes a weird warning here
        //case q"scala.StringContext.apply($stringContext).s(..$args)" =>
        val namedArgs = ArgumentNameExtractionMacro.recoverArgNames(c)(args.map(p => c.Expr(p)))
        reifyContext(c)(stringContext, namedArgs)

      case Literal(c.universe.Constant(s)) =>
        val emptyArgs = reify(List.empty)
        val sc = q"StringContext(${s.toString})"
        reifyContext(c)(sc, emptyArgs)

      case other =>
        c.warning(other.pos,
          s"""Complex expression found as an input for a logger: ${other.toString()} ; ${showRaw(other)}.
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |${ArgumentNameExtractionMacro.example}
             |""".stripMargin)

        val emptyArgs = reify(List.empty)
        /* reify {
          val repr = c.Expr[String](Literal(Constant(c.universe.showCode(other)))).splice
          ...
        */
        val sc = q"StringContext($other)"
        reifyContext(c)(sc, emptyArgs)
    }
  }

}
