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
  case class ValueNode[T](value: T) extends TextTree[T]

  case class StringNode[T](value: String) extends TextTree[T]

  case class Node[T](chunks: NEList[TextTree[T]]) extends TextTree[T]

  case class Shift[T](nested: TextTree[T], shift: Int) extends TextTree[T]

  case class Trim[T](nested: TextTree[T]) extends TextTree[T]

  implicit class TextTreeSeqOps[T](target: Seq[TextTree[T]]) {
    def join(sep: String): TextTree[T] = {
      if (target.isEmpty) {
        StringNode("")
      } else {
        NEList.from(target.flatMap(t => Seq(t, StringNode[T](sep))).init) match {
          case Some(value) =>
            Node(value)
          case None =>
            StringNode("")
        }
      }
    }
  }

  implicit class TextTreeGenericOps[T](target: TextTree[T]) {
    def dump: String = mapRender(_.toString)

    def mapRender(f: T => String): String = {
      target match {
        case v: ValueNode[T] => f(v.value)
        case s: StringNode[T] => s.value
        case s: Shift[T] => s.nested.mapRender(f).shift(s.shift)
        case t: Trim[T] => t.nested.mapRender(f).trim
        case n: Node[T] => n.chunks.map(_.mapRender(f)).mkString
      }
    }

    def flatten: TextTree[T] = {
      target match {
        case v: ValueNode[T] => Node(NEList(v))
        case s: StringNode[T] => Node(NEList(s))
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

    def map[U](f: T => U): TextTree[U] = {
      target match {
        case v: ValueNode[T] => ValueNode(f(v.value))
        case s: StringNode[T] => StringNode(s.value)
        case s: Shift[T] => Shift(s.nested.map(f), s.shift)
        case s: Trim[T] => Trim(s.nested.map(f))
        case n: Node[T] => Node(n.chunks.map(_.map(f)))
      }
    }

    def values: Seq[T] = {
      target match {
        case v: ValueNode[T] => Seq(v.value)
        case _: StringNode[T] => Seq.empty
        case t: Trim[T] => t.nested.values
        case s: Shift[T] => s.nested.values
        case n: Node[T] => n.chunks.toSeq.flatMap(_.values)
      }
    }

    def stripMargin(marginChar: Char): TextTree[T] = {
      target match {
        case v: ValueNode[T] => v
        case s: StringNode[T] => s
        case s: Shift[T] => s
        case t: Trim[T] => t
        case n: Node[T] =>
          Node(n.chunks.map {
            case v: ValueNode[T] => v
            case n: Node[T] => n
            case s: Shift[T] => s
            case t: Trim[T] => t
            case s: StringNode[T] => StringNode(s.value.stripMargin(marginChar))
          })
      }
    }

    def stripMargin: TextTree[T] = stripMargin('|')

    def trim: TextTree[T] = {
      Trim(target)
    }

    def shift(pad: Int): TextTree[T] = {
      Shift(target, pad)
    }
  }

  implicit class Quote(val sc: StringContext) extends AnyVal {
    def q[T](args: InterpolationArg[T]*): Node[T] = {
      assert(sc.parts.length == args.length + 1)

      val seq = sc.parts
        .zip(args)
        .flatMap {
          case (t, v) =>
            List(StringNode[T](t), v.asNode)
        }
        .reverse

      Node(NEList(StringNode[T](sc.parts.last), seq).reverse)
    }
  }

  trait InterpolationArg[+T] {
    def asNode: TextTree[T]
  }

  trait LowPrioInterpolationArg {
    implicit def forT[T](t: T): InterpolationArg[T] = new InterpolationArg[T] {
      override def asNode: TextTree[T] = ValueNode(t)
    }

    implicit def forNodeT[T](node: TextTree[T]): InterpolationArg[T] =
      new InterpolationArg[T] {
        override def asNode: TextTree[T] = node
      }

  }

  object InterpolationArg extends LowPrioInterpolationArg {
    implicit def forString[T](t: String): InterpolationArg[T] =
      new InterpolationArg[T] {
        override def asNode: TextTree[T] = StringNode(t)
      }

    implicit def forNodeNothing[T](
      node: TextTree[Nothing]
    ): InterpolationArg[T] =
      new InterpolationArg[T] {
        override def asNode: TextTree[T] = node.asInstanceOf[TextTree[T]]
      }

  }
}
