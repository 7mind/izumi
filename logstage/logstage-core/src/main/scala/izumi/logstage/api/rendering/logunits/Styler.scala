package izumi.logstage.api.rendering.logunits

import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.Log
import izumi.logstage.api.rendering.logunits.Renderer.Aggregate
import izumi.logstage.api.rendering.{ConsoleColors, RenderingOptions}

trait Styler extends Renderer

object Styler {

  case class StringLimits(minimized: Option[Int], elipsed: Boolean, size: Option[Int])

  abstract class Transformer(sub: Seq[Renderer]) extends Styler {
    override final def render(entry: Log.Entry, context: RenderingOptions): LETree = {
      LETree.TextNode(transform(new Aggregate(sub).render(entry, context)))
    }

    protected def transform(out: String): String
  }

  class TrailingSpace(sub: Seq[Renderer]) extends Transformer(sub) {
    override protected def transform(out: String): String = {
      if (out.nonEmpty) {
        out + " "
      } else {
        out
      }
    }
  }

  sealed trait TrimType
  object TrimType {
    case object Left extends TrimType
    case object Right extends TrimType
    case object Center extends TrimType
  }

  sealed trait PadType
  object PadType {
    case object Left extends PadType
    case object Right extends PadType
  }

  class AdaptivePad(sub: Seq[Renderer], initialLength: Int, pad: PadType, symbol: Char) extends Transformer(sub) {
    // atomics are not supported on sjs
    @volatile var maxSize: Int = initialLength

    override protected def transform(out: String): String = {
      val size = out.length

      val newSize = {
        // not perfect and there is a race, though that's not critical
        val current = maxSize
        val updated = math.max(current, size)
        maxSize = updated
        updated
      }

      pad match {
        case PadType.Left =>
          out.leftPad(newSize, symbol)
        case PadType.Right =>
          out.padTo(newSize, symbol)
      }
    }
  }


  class Pad(sub: Seq[Renderer], length: Int, pad: PadType, symbol: Char) extends Transformer(sub) {
    override protected def transform(out: String): String = {
      pad match {
        case PadType.Left =>
          out.leftPad(length, symbol)
        case PadType.Right =>
          out.padTo(length, symbol)
      }
    }
  }

  class Compact(sub: Seq[Renderer], takeRight: Int) extends Transformer(sub) {
    override protected def transform(out: String): String = {
      out.minimize(takeRight)
    }
  }

  class Trim(sub: Seq[Renderer], maxLength: Int, trim: TrimType, ellipsis: Option[String]) extends Transformer(sub) {
    override protected def transform(out: String): String = {
      trim match {
        case TrimType.Left =>
          ellipsis match {
            case Some(value) =>
              out.leftEllipsed(maxLength, value)
            case None =>
              out.take(maxLength)
          }

        case TrimType.Right =>
          ellipsis match {
            case Some(value) =>
              out.rightEllipsed(maxLength, value)
            case None =>
              out.takeRight(maxLength)
          }

        case TrimType.Center =>
          out.centerEllipsed(maxLength, ellipsis)
      }
    }
  }

  class Colored(color: String, sub: Seq[Renderer]) extends Styler {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree = {
      if (sub.tail.isEmpty) {
        LETree.ColoredNode(color, sub.head.render(entry, context))
      } else {
        LETree.ColoredNode(color, LETree.Sequence(sub.map(_.render(entry, context))))
      }
    }
  }

  class LevelColor(sub: Seq[Renderer]) extends Styler {
    override def render(entry: Log.Entry, context: RenderingOptions): LETree = {
      val color = ConsoleColors.logLevelColor(entry.context.dynamic.level)
      if (sub.tail.isEmpty) {
        LETree.ColoredNode(color, sub.head.render(entry, context))
      } else {
        LETree.ColoredNode(color, LETree.Sequence(sub.map(_.render(entry, context))))
      }
    }
  }

}

