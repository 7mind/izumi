    package izumi.logstage.macros

import izumi.logstage.api.Log.{LogArg, Message}

import scala.collection.mutable
import scala.reflect.macros.blackbox


class LogMessageMacro(cc: blackbox.Context) extends LogMessageMacro0(cc, false)

class LogMessageMacroStrict(cc: blackbox.Context) extends LogMessageMacro0(cc, true)

class LogMessageMacro0[C <: blackbox.Context](val c: C, strict: Boolean) {
  private final val nameExtractor = new ArgumentNameExtractionMacro[c.type](c, strict)

  def logMessageMacro(message: c.Expr[String]): c.Expr[Message] = {
    import c.universe._

    message.tree match {
      case Typed(tree, _) =>
        processExpr(tree, isMultiline = false)
      case tree =>
        processExpr(tree, isMultiline = false)
    }
  }

  @scala.annotation.tailrec
  private[this] def processExpr(message: c.Tree, isMultiline: Boolean): c.Expr[Message] = {
    import c.universe._

    sealed trait Chunk {
      def tree: c.Tree
    }

    object Chunk {

      sealed trait Primary extends Chunk

      sealed trait AbstractElement extends Chunk


      case class Element(lit: c.universe.Literal) extends Primary with AbstractElement {
        override def tree: c.Tree = lit
      }

      case class Argument(tree: c.Tree) extends Primary

      case class ExprElement(tree: c.Tree) extends Chunk with AbstractElement

    }

    case class Out(parts: Seq[Chunk.Primary]) {
      def arguments: Seq[c.Tree] = {
        balanced.collect { case Chunk.Argument(expr) => expr }
      }

      def makeStringContext(isMultiline: Boolean): c.Tree = {
        val elements = balanced.collect { case e: Chunk.AbstractElement => e.tree }
        val listExpr = if (isMultiline)
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
      def unapply(arg: c.universe.Tree): Option[Out] = {
        arg match {
          case Apply(Select(applyselect, TermName("$plus")), args: List[c.Tree]) =>
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
                c.warning(c.enclosingPosition, s"Something is wrong with this expression, please report this as a bug: $applyselect, ${c.universe.showRaw(applyselect)}")
                Some(sub)
            }

          case _ => None
        }
      }

      private def toChunk(head: c.universe.Tree): Chunk.Primary = {
        val chunk = head match {
          case t@Literal(Constant(_: String)) =>
            Chunk.Element(t)
          case t@Literal(Constant(_)) =>
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
        reifyContext(sc, namedArgs)

      case Apply(Select(stringContext@Apply(Select(Select(Ident(TermName("scala")), TermName("StringContext")), TermName("apply")), _), TermName("s")), args: List[c.Tree]) =>
        // qq causes a weird warning here
        //case q"scala.StringContext.apply($stringContext).s(..$args)" =>
        val namedArgs = nameExtractor.recoverArgNames(args)
        reifyContext(stringContext, namedArgs)

      case Select(Apply(_, List(Apply(Select(stringContext@Apply(Select(Select(Ident(TermName("scala")), TermName("StringContext")), TermName("apply")), _), TermName("s")), args: List[c.Tree]))), TermName("stripMargin")) =>
        val namedArgs = nameExtractor.recoverArgNames(args)
        val sc = q"""_root_.scala.StringContext($stringContext.parts.map(_.stripMargin): _*)"""
        reifyContext(sc, namedArgs)

      // support .stripMargin in scala 2.13
      case Select(Apply(_, List(Typed(tree, _))), TermName("stripMargin")) =>
        processExpr(tree, isMultiline = true)

      case Literal(c.universe.Constant(s)) =>
        val emptyArgs = reify(List.empty)
        val sc = q"_root_.scala.StringContext(${s.toString})"
        reifyContext(sc, emptyArgs)

      case other =>
        c.warning(other.pos,
          s"""Complex expression found as an input for a logger: ${other.toString()} ; ${showRaw(other)}.
             |
             |But Logstage expects you to use string interpolations instead, such as:
             |${nameExtractor.example}
             |""".stripMargin)

        val emptyArgs = reify(List.empty)
        /* reify {
          val repr = c.Expr[String](Literal(Constant(c.universe.showCode(other)))).splice
          ...
        */
        val sc = q"_root_.scala.StringContext($other)"
        reifyContext(sc, emptyArgs)
    }
  }

  private[this] def reifyContext(stringContext: c.universe.Tree, namedArgs: c.Expr[List[LogArg]]): c.Expr[Message] = {
    import c.universe._
    reify {
      Message(
        c.Expr[StringContext](stringContext).splice
        , namedArgs.splice
      )
    }
  }

}
