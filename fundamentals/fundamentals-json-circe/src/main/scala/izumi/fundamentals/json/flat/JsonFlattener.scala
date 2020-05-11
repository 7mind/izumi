package izumi.fundamentals.json.flat

import com.github.ghik.silencer.silent
import io.circe.Json
import izumi.functional.IzEither.EitherBiAggregate

import scala.annotation.switch
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

class JsonFlattener {
  import JsonFlattener._
  import PathElement._
  import izumi.fundamentals.platform.strings.IzEscape

  private final val NullT = 'n'
  private final val BoolT = 'b'
  private final val LongT = 'l'
  private final val FloatT = 'f'
  private final val StringT = 's'
  private final val AggT = 'a'

  private final val tpes = Set(NullT, BoolT, LongT, FloatT, StringT, AggT)

  private final val controlChars = Set('.', '[', ']')
  private final val escapeChar = '\\'
  private final val escape = new IzEscape(controlChars, escapeChar)


  def flatten(node: Json): Seq[(String, String)] = {
    flatten(node, Seq.empty)
  }

  private def flatten(node: Json, prefix: Seq[PathElement]): Seq[(String, String)] = {
    node.fold(
      Seq(makePath(prefix, "null") -> "null"),
      b => Seq(makePath(prefix, "bool") -> b.toString),
      n => {
        n.toBigInt
          .map {
            bi =>
              Seq(makePath(prefix, "long") -> bi.toString)
          }
          .getOrElse {
            Seq(makePath(prefix, "float") -> n.toBigDecimal.map(_.toString()).getOrElse(n.toDouble.toString))
          }

      },
      s => Seq(makePath(prefix, "str") -> s),
      a => {
        val out = a.zipWithIndex.flatMap {
          case (element, idx) =>
            flatten(element, prefix :+ PathElement.Index(idx))
        }
        if (out.nonEmpty) {
          out
        } else {
          Seq(makePath(prefix, "agg") -> "arr")
        }
      },
      o => {
        val out = o.toIterable.flatMap {
          case (name, value) =>
            flatten(value, prefix :+ PathElement.ObjectName(name))
        }.toSeq
        if (out.nonEmpty) {
          out
        } else {
          Seq(makePath(prefix, "agg") -> "obj")
        }
      },
    )
  }

  private def makePath(p: Seq[PathElement], tpe: String): String = {
    val prefix = p.map {
      case ObjectName(name) =>
        escape.escape(name)
      case Index(idx) =>
        s"[$idx]"
    }
    s"${prefix.mkString(".")}:$tpe"
  }


  def inflate(pairs: Seq[(String, String)]): Either[List[UnpackFailure], Json] = {
    val maybePaths = pairs.map {
      case (k, v) =>
        parsePath(k).map {
          case (path, tpe) =>
            (path, tpe, v)
        }
    }.biAggregate

    for {
      p <- maybePaths
      out <- inflateParsed(p)
    } yield out
  }

  @silent("return statement uses an exception")
  private def parsePath(path: String): Either[List[UnpackFailure], (Seq[PathElement], Char)] = {
    val idx = path.lastIndexOf(':')
    if (idx < 0) {
      Left(List())
    } else {
      val (p, tpe) = path.splitAt(idx)

      if (tpe.length < 2) {
        return Left(List(UnpackFailure.BadPathFormat(path)))
      }
      val rtpe = tpe.charAt(1)
      if (!tpes.contains(rtpe)) {
        return Left(List(UnpackFailure.UnexpectedType(tpe.substring(1), path)))
      }

      val buf = new ArrayBuffer[PathElement]()
      var inEscape = false
      var start = 0

      for (idx <- p.indices) {
        val c = p.charAt(idx)
        if (inEscape) {
          inEscape = false
        } else if (c == escapeChar) {
          inEscape = true
        } else if (c == '.') {
          addChunk(p, buf, start, idx) match {
            case Left(value) =>
              return Left(value)
            case Right(_) =>
          }
          start = idx + 1
        }
      }

      if (inEscape) {
        Left(List(UnpackFailure.UnterminatedEscapeSequence(path)))
      } else {
        if (start < p.length) {
          addChunk(p, buf, start, p.length) match {
            case Left(value) =>
              return Left(value)
            case Right(_) =>
          }
        }
        Right((buf.toVector, rtpe))
      }

    }
  }

  private def addChunk(p: String, buf: ArrayBuffer[PathElement], start: Int, idx: Int): Either[List[UnpackFailure], Unit] = {
    val chunk = p.substring(start, idx)
    if (chunk.startsWith("[") && chunk.endsWith("]")) {
      try {
        buf.append(Index(chunk.substring(1, chunk.length - 1).toInt))
        Right(())
      } catch {
        case NonFatal(t) =>
          Left(List(UnpackFailure.PathIndexParsingFailed(p, t)))
      }
    } else {
      buf.append(ObjectName(escape.unescape(chunk)))
      Right(())
    }
  }


  private def inflateParsed(pairs: Seq[(Seq[PathElement], Char, String)]): Either[List[UnpackFailure], Json] = {
    val grouped = pairs.groupBy(_._1.headOption)

    grouped.get(None) match {
      case Some(value +: t) if t.isEmpty =>
        parse(value._2, value._3)

      case Some(value) =>
        for {
          elements <- value.map(v => parse(v._2, v._3)).biAggregate
        } yield {
          Json.fromValues(elements)
        }
      case None =>
        val grouped2 = pairs.groupBy(_._1.head)

        if (grouped2.nonEmpty && grouped2.keys.forall(_.isInstanceOf[Index])) {
          for {
            elements <- grouped2.toSeq.sortBy(_._1.asInstanceOf[Index].idx).map(_._2).map(inflateParsedNext).biAggregate
          } yield {
            Json.fromValues(elements)
          }
        } else if (grouped2.keys.forall(_.isInstanceOf[ObjectName])) {
          for {
            elements <- grouped2.map {
              case (k, v) =>
                for {
                  field <- inflateParsedNext(v)
                } yield {
                  escape.unescape(k.asInstanceOf[ObjectName].name) -> field
                }
            }.toSeq.biAggregate
          } yield {
            Json.fromFields(elements)
          }
        } else {
          Left(List(UnpackFailure.StructuralFailure(pairs)))
        }
    }
  }

  @inline private[this] def drop(v: (Seq[PathElement], Char, String)): (Seq[PathElement], Char, String) = {
    v match {
      case (path, tpe, value) =>
        (path.drop(1), tpe, value)
    }
  }

  @inline private[this] def inflateParsedNext(pairs: Seq[(Seq[PathElement], Char, String)]): Either[List[UnpackFailure], Json] = {
    inflateParsed(pairs.map(drop))
  }

  private def parse(tpe: Char, value: String): Either[List[UnpackFailure], Json] = {
    try {
      Right {
        (tpe: @switch) match {
          case NullT => Json.Null
          case BoolT => Json.fromBoolean(value.toBoolean)
          case LongT => Json.fromLong(value.toLong)
          case FloatT => Json.fromBigDecimal(BigDecimal.apply(value))
          case StringT => Json.fromString(value)
          case AggT => value match {
            case "obj" =>
              Json.obj()
            case "arr" =>
              Json.arr()
          }
        }
      }
    } catch {
      case NonFatal(t) =>
        Left(List(UnpackFailure.ScalarParsingFailed(tpe.toString, value, t)))
    }
  }

}

object JsonFlattener {
  sealed trait PathElement
  object PathElement {
    final case class ObjectName(name: String) extends PathElement
    final case class Index(idx: Int) extends PathElement
  }

  sealed trait UnpackFailure
  object UnpackFailure {
    final case class ScalarParsingFailed(tpe: String, value: String, t: Throwable) extends UnpackFailure
    final case class UnterminatedEscapeSequence(path: String) extends UnpackFailure
    final case class UnexpectedType(tpe: String, path: String) extends UnpackFailure
    final case class BadPathFormat(path: String) extends UnpackFailure
    final case class PathIndexParsingFailed(path: String, t: Throwable) extends UnpackFailure
    final case class StructuralFailure(structure: Seq[(Seq[PathElement], Char, String)]) extends UnpackFailure
  }
}
