package izumi.fundamentals.platform.strings

import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.strings.IzString.*

import scala.language.implicitConversions

/** This is a convenience utility allowing to build trees of plain text and typed values.
  *
  * This utility is extremely useful for various template engines, query builders and transpilers
  */
sealed trait TextTree[+T]

object TextTree {
  /** Create a typed value node */
  def value[T](value: T): TextTree[T] = ValueNode(value)

  /** Create a text node with escape sequence processing */
  def text[T](value: String): TextTree[T] = StringNode(value)

  /** Create a verbatim text node without escape sequence processing */
  def verbatim[T](value: String): TextTree[T] = VerbatimNode(value)

  /** A typed value that will be rendered using a provided function */
  case class ValueNode[+T](value: T) extends TextTree[T]

  /** Plain text with escape sequences (like \n) processed during rendering */
  case class StringNode(value: String) extends TextTree[Nothing]

  /** Plain text with escape sequences kept literal (raw strings) */
  case class VerbatimNode(value: String) extends TextTree[Nothing]

  /** A composite node containing multiple child trees */
  case class Node[+T](chunks: NEList[TextTree[T]]) extends TextTree[T]

  /** Indents nested tree by specified number of spaces on each line */
  case class Shift[+T](nested: TextTree[T], shift: Int) extends TextTree[T]

  /** Trims whitespace from the beginning and end of nested tree */
  case class Trim[+T](nested: TextTree[T]) extends TextTree[T]

  /** Operations for sequences of TextTrees */
  implicit class TextTreeSeqOps[T](target: Seq[TextTree[T]]) {
    /** Join trees with a separator string between elements */
    def join(sep: String): TextTree[T] = {
      if (target.isEmpty) {
        StringNode("")
      } else {
        NEList.from(target.flatMap(t => Seq(t, StringNode(sep))).init) match {
          case Some(value) =>
            Node(value)
          case None =>
            StringNode("")
        }
      }
    }

    /** Join trees with begin/end delimiters and optional indentation (default: 2 spaces) */
    def join(begin: String, sep: String, end: String, shift: Option[Int] = Some(2)): TextTree[T] = {
      val joined = target.join(sep)
      val middle = shift match {
        case Some(value) => joined.shift(value)
        case None => joined
      }
      q"$begin$middle$end"
    }

    /** Join trees with newlines */
    def joinN(): TextTree[T] = {
      target.join("\n")
    }

    /** Join trees with double newlines */
    def joinNN(): TextTree[T] = {
      target.join("\n\n")
    }
  }

  /** Core operations for TextTree */
  implicit final class TextTreeGenericOps[T](private val target: TextTree[T]) {
    /** Get the last character from string nodes, if any */
    def last: Option[Char] = target match {
      case ValueNode(_) => None
      case StringNode(value) => value.lastOption
      case VerbatimNode(value) => value.lastOption
      case Node(chunks) => chunks.toList.filter(_.nonEmpty).lastOption.flatMap(_.last)
      case Shift(nested, _) => nested.last
      case Trim(nested) => nested.rawChunks.mkString.trim.lastOption
    }

    /** Get a list of string nodes (values ingored) */
    def rawChunks: Seq[String] = target match {
      case ValueNode(_) => Seq.empty
      case StringNode(value) => Seq(StringContext.processEscapes(value))
      case VerbatimNode(value) => Seq(value)
      case Node(chunks) => chunks.toList.flatMap(_.rawChunks)
      case Shift(nested, _) => nested.rawChunks
      case Trim(nested) => nested.rawChunks
    }

    /** Convert tree values using an implicit conversion */
    def as[W](implicit conv: T => W): TextTree[W] = {
      target.map(conv)
    }

    /** Render tree to string using .toString on values */
    def dump: String = mapRender(_.toString)

    /** Render tree to string (only for trees without typed values) */
    def render(implicit ev: T =:= Nothing): String = {
      target match {
        case _: ValueNode[Nothing] @unchecked => throw new IllegalStateException()
        case s: StringNode => StringContext.processEscapes(s.value)
        case s: VerbatimNode => s.value
        case s: Shift[T] => s.nested.render.shift(s.shift)
        case t: Trim[T] => t.nested.render.trim
        case n: Node[T] => n.chunks.map(_.render).mkString
      }
    }

    /** Render tree to string, converting values with provided function */
    def mapRender(f: T => String): String = {
      target match {
        case v: ValueNode[T] => f(v.value)
        case s: StringNode => StringContext.processEscapes(s.value)
        case s: VerbatimNode => s.value
        case s: Shift[T] => s.nested.mapRender(f).shift(s.shift)
        case t: Trim[T] => t.nested.mapRender(f).trim
        case n: Node[T] => n.chunks.map(_.mapRender(f)).mkString
      }
    }

    /** Check if tree is empty (no values and only empty text) */
    def isEmpty: Boolean = {
      target match {
        case _: ValueNode[T] => false
        case s: StringNode => s.value.isEmpty
        case s: VerbatimNode => s.value.isEmpty
        case s: Shift[T] => s.nested.isEmpty
        case t: Trim[T] => t.nested.isEmpty
        case n: Node[T] =>
          n.chunks.forall(_.isEmpty)
      }
    }

    /** Check if tree is non-empty */
    def nonEmpty: Boolean = !isEmpty

    /** Flatten nested Node structures into a single-level Node */
    def flatten: TextTree[T] = {
      target match {
        case v: ValueNode[T] => Node(NEList(v))
        case s: StringNode => Node(NEList(s))
        case s: VerbatimNode => Node(NEList(s))
        case s: Shift[T] => Shift(s.flatten, s.shift)
        case t: Trim[T] => Trim(t.flatten)
        case n: Node[T] =>
          Node(n.chunks.flatMap {
            _.flatten match {
              case n: Node[T] => n.chunks
              case o => NEList(o)
            }
          })
      }
    }

    /** Transform all typed values in the tree */
    def map[U](f: T => U): TextTree[U] = {
      target match {
        case v: ValueNode[T] => ValueNode(f(v.value))
        case s: StringNode => StringNode(s.value)
        case s: VerbatimNode => VerbatimNode(s.value)
        case s: Shift[T] => Shift(s.nested.map(f), s.shift)
        case s: Trim[T] => Trim(s.nested.map(f))
        case n: Node[T] => Node(n.chunks.map(_.map(f)))
      }
    }

    /** Apply a side-effecting function to all typed values */
    def foreach(f: T => Unit): Unit = {
      target match {
        case v: ValueNode[T] => f(v.value)
        case _: StringNode => ()
        case _: VerbatimNode => ()
        case s: Shift[T] => s.nested.foreach(f)
        case s: Trim[T] => s.nested.foreach(f)
        case n: Node[T] => n.chunks.foreach(_.foreach(f))
      }
    }

    /** Extract all typed values from the tree */
    def values: Seq[T] = {
      target match {
        case v: ValueNode[T] => Seq(v.value)
        case _: StringNode => Seq.empty
        case _: VerbatimNode => Seq.empty
        case t: Trim[T] => t.nested.values
        case s: Shift[T] => s.nested.values
        case n: Node[T] => n.chunks.toSeq.flatMap(_.values)
      }
    }

    /** Strip margin from string nodes using custom margin character */
    def stripMargin(marginChar: Char): TextTree[T] = {
      target match {
        case v: ValueNode[T] => v
        case s: StringNode => s
        case s: VerbatimNode => s
        case s: Shift[T] => s
        case t: Trim[T] => t
        case n: Node[T] =>
          Node(n.chunks.map {
            case v: ValueNode[T] => v
            case n: Node[T] => n
            case s: Shift[T] => s
            case t: Trim[T] => t
            case s: StringNode => StringNode(s.value.stripMargin(marginChar))
            case s: VerbatimNode => VerbatimNode(s.value.stripMargin(marginChar))
          })
      }
    }

    /** Strip margin from string nodes using default '|' margin character */
    def stripMargin: TextTree[T] = stripMargin('|')

    /** Wrap tree in Trim node to trim whitespace when rendering */
    def trim: TextTree[T] = {
      Trim(target)
    }

    /** Wrap tree in Shift node to indent by specified spaces when rendering */
    def shift(pad: Int): TextTree[T] = {
      Shift(target, pad)
    }
  }

  /** String interpolators for building TextTrees */
  implicit class Quote(val sc: StringContext) extends AnyVal {
    /** Build a TextTree with escape sequence processing (e.g., q"test\n" renders with actual newline) */
    def q[T](args: InterpolationArg[T]*): TextTree[T] = {
      assert(sc.parts.length == args.length + 1)
      val seq = sc.parts
        .zip(args)
        .flatMap {
          case (t, v) =>
            List(StringNode(t), v.asNode)
        }
        .reverse

      Node(NEList(StringNode(sc.parts.last), seq).reverse)
    }

    /** Build a TextTree with verbatim/raw strings (e.g., qv"test\n" renders as literal "test\n") */
    def qv[T](args: InterpolationArg[T]*): TextTree[T] = {
      assert(sc.parts.length == args.length + 1)
      val seq = sc.parts
        .zip(args)
        .flatMap {
          case (t, v) =>
            List(VerbatimNode(t), v.asNode)
        }
        .reverse

      Node(NEList(VerbatimNode(sc.parts.last), seq).reverse)
    }
  }

  /** Typeclass for values that can be interpolated into TextTree interpolators */
  trait InterpolationArg[+T] {
    def asNode: TextTree[T]
  }

  object InterpolationArg extends LowPrioInterpolationArg_1

  protected trait LowPrioInterpolationArg_1 extends LowPrioInterpolationArg_2 {
    implicit def arg_from_String[T](t: String): InterpolationArg[T] = new InterpolationArg[T] {
      override def asNode: TextTree[T] = StringNode(t)
    }

    implicit def arg_from_Nothing[T](
      node: TextTree[Nothing]
    ): InterpolationArg[T] = new InterpolationArg[T] {
      override def asNode: TextTree[T] = node.asInstanceOf[TextTree[T]]
    }
  }

  protected trait LowPrioInterpolationArg_2 {
    implicit def value[T](t: T): InterpolationArg[T] = new InterpolationArg[T] {
      override def asNode: TextTree[T] = ValueNode(t)
    }

    implicit def subtree[T](node: TextTree[T]): InterpolationArg[T] = new InterpolationArg[T] {
      override def asNode: TextTree[T] = node
    }
  }

  /** Language-specific rendering styles */
  object style {
    /** C-style code generation helpers */
    object c {
      /** Operations for C-style statement termination */
      implicit class TextTreeCStyleOps[T](target: TextTree[T]) {
        /** Add semicolon if statement doesn't end with '}' */
        def endC(): TextTree[T] = {
          target.last match {
            case Some('}') => target
            case None => target
            case _ => q"$target;"
          }
        }
      }

      /** Operations for C-style sequence joining */
      implicit class TextTreeSeqCStyleOps[T](target: Seq[TextTree[T]]) {
        /** Join statements with newlines, adding semicolons except after '}' */
        def joinCN(): TextTree[T] = {
          if (target.isEmpty) {
            StringNode("")
          } else {
            val withSeparators = target.flatMap {
              t =>
                if (t.last.contains('}')) {
                  Seq(t, StringNode("\n"))
                } else {
                  Seq(t, StringNode(";\n"))
                }

            }.init

            NEList.from(withSeparators) match {
              case Some(value) =>
                Node(value)
              case None =>
                StringNode("")
            }
          }
        }

      }
    }
  }
}
