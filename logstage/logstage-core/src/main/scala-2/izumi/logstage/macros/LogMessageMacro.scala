package izumi.logstage.macros

import izumi.logstage.api.Log.{LogArg, Message}

import scala.collection.mutable
import scala.reflect.macros.blackbox

final class LogMessageMacro(override val c: blackbox.Context) extends LogMessageMacro0(c, false)

final class LogMessageMacroStrict(override val c: blackbox.Context) extends LogMessageMacro0(c, true)

class LogMessageMacro0[C <: blackbox.Context](val c: C, strict: Boolean) {
  private final val nameExtractor = new ArgumentNameExtractionMacro[c.type](c, strict)

  import c.universe._

  final def logMessageMacro(message: c.Expr[String]): c.Expr[Message] = {
    message.tree match {
      case Typed(tree, _) =>
        processExpr(tree, isMultiline = false)
      case tree =>
        processExpr(tree, isMultiline = false)
    }
  }

  @scala.annotation.tailrec
  private def processExpr(message: c.Tree, isMultiline: Boolean): c.Expr[Message] = {
    sealed trait Chunk {
      def tree: Tree
    }
    object Chunk {
      sealed trait Primary extends Chunk
      final case class Argument(tree: Tree) extends Primary

      sealed trait AbstractElement extends Chunk
      final case class Element(lit: Literal) extends Primary with AbstractElement {
        override def tree: Tree = lit
      }
      final case class ExprElement(tree: Tree) extends AbstractElement
    }

    case class Out(parts: Seq[Chunk.Primary]) {
      def arguments: Seq[Tree] = {
        balanced.collect { case Chunk.Argument(expr) => expr }
      }

      def makeStringContext(isMultiline: Boolean): Tree = {
        val elements = balanced.collect { case e: Chunk.AbstractElement => e.tree }
        val listExpr =
          if (isMultiline)
            c.Expr[Seq[String]](q"Seq(..$elements).map(_.stripMargin)")
          else
            c.Expr[Seq[String]](q"Seq(..$elements)")

        val scParts = reify {
          listExpr.splice
        }
        val sc = q"StringContext($scParts :_*)"
        sc
      }

      // we need to build StringContext compatible list of chunks and arguments where elements of the same type cannot occur consequently
      private lazy val balanced: Seq[Chunk] = {
        val balancedParts = mutable.ArrayBuffer[Chunk]()

        val emptystring = Chunk.Element(Literal(Constant("")))

        parts.foreach {
          case e: Chunk.Element =>
            balancedParts.lastOption match {
              case Some(value: Chunk.Element) =>
                balancedParts.remove(balancedParts.size - 1)
                balancedParts.append(Chunk.ExprElement(q"${value.tree} + ${e.tree}"))
              case _ =>
                balancedParts.append(e)
            }

          case a: Chunk.Argument =>
            balancedParts.lastOption match {
              case Some(_: Chunk.Argument) =>
                balancedParts.append(emptystring)
              case None =>
                balancedParts.append(emptystring)
              case _ =>
            }

            balancedParts.append(a)
        }

        balancedParts.lastOption match {
          case Some(_: Chunk.Argument) =>
            balancedParts.append(emptystring)
          case _ =>
        }
        balancedParts.toSeq
      }
    }

    object PlusExtractor {
      def unapply(arg: Tree): Option[Out] = {
        arg match {
          case Apply(Select(applyselect, TermName("$plus")), args: List[Tree]) =>
            val sub = applyselect match {
              case PlusExtractor(out) =>
                out

              case o =>
                val chunk = toChunk(o)
                Out(Seq(chunk))
            }

            // in sequence of string concatenations ("" + "" + "") we expect only one argument to be at the right side
            args match {
              case head :: Nil =>
                val chunk = toChunk(head)
                Some(sub.copy(parts = sub.parts :+ chunk))

              case _ =>
                c.warning(c.enclosingPosition, s"Something is wrong with this expression, please report this as a bug: $applyselect, ${showRaw(applyselect)}")
                Some(sub)
            }

          case _ => None
        }
      }

      private def toChunk(head: Tree): Chunk.Primary = {
        val chunk = head match {
          case t @ Literal(Constant(_: String)) =>
            Chunk.Element(t)
          case t @ Literal(Constant(_)) =>
            Chunk.Argument(t)
          case argexpr =>
            Chunk.Argument(argexpr)
        }
        chunk
      }
    }

    message match {
      case PlusExtractor(lst) =>
        val namedArgs = nameExtractor.recoverArgNames(lst.arguments)
        val sc = lst.makeStringContext(isMultiline)
        createMessageExpr(sc, namedArgs)

      case Literal(Constant(s)) =>
        val emptyArgs = reify(List.empty)
        val sc = if (isMultiline) {
          q"_root_.scala.StringContext(${s.toString}.stripMargin)"
        } else {
          q"_root_.scala.StringContext(${s.toString})"
        }
        createMessageExpr(sc, emptyArgs)

      case Apply(
            Select(stringContext @ Apply(Select(Select(Ident(TermName("scala")), TermName("StringContext")), TermName("apply")), _), TermName("s")),
            args: List[Tree],
          ) =>
        // qq causes a weird warning here
        // case q"scala.StringContext.apply($stringContext).s(..$args)" =>
        val namedArgs = nameExtractor.recoverArgNames(args)
        createMessageExpr(stringContext, namedArgs)

      case Select(
            Apply(
              _,
              List(
                Apply(
                  Select(stringContext @ Apply(Select(Select(Ident(TermName("scala")), TermName("StringContext")), TermName("apply")), _), TermName("s")),
                  args: List[Tree],
                )
              ),
            ),
            TermName("stripMargin"),
          ) =>
        val namedArgs = nameExtractor.recoverArgNames(args)
        val sc = q"""_root_.scala.StringContext($stringContext.parts.map(_.stripMargin)*)"""
        createMessageExpr(sc, namedArgs)

      // support .stripMargin in scala 2.13 and 2.13.10
      case Select(Apply(_, List(arg)), TermName("stripMargin")) =>
        arg match {
          case Typed(tree, _) =>
            processExpr(tree, isMultiline = true)
          case tree =>
            processExpr(tree, isMultiline = true)
        }

      case Typed(tree, _) =>
        processExpr(tree, isMultiline)

      case other =>
        c.warning(
          other.pos,
          s"""Complex expression found as an input for a logger: ${other.toString()}
             |
             |Tree: ${showRaw(other)}
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |${nameExtractor.example}
             |""".stripMargin,
        )

        val emptyArgs = reify(List.empty)
        /* reify {
          val repr = c.Expr[String](Literal(Constant(showCode(other)))).splice
          ...
         */
        val sc = q"_root_.scala.StringContext($other)"
        createMessageExpr(sc, emptyArgs)
    }
  }

  private def createMessageExpr(stringContext: c.Tree, namedArgs: c.Expr[List[LogArg]]): c.Expr[Message] = {
    reify {
      Message(
        c.Expr[StringContext](stringContext).splice,
        namedArgs.splice,
      )
    }
  }

}
