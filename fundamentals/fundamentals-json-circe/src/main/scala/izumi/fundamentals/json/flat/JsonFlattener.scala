package izumi.fundamentals.json.flat

import scala.annotation.nowarn
import io.circe.Json
import izumi.functional.IzEither.EitherBiAggregate

import scala.annotation.switch
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal
import JsonFlattener.*
import PathElement.*
import izumi.fundamentals.platform.strings.CharEscape

class JsonFlattener {

  private final val NullT = 'n'
  private final val BoolT = 'b'
  private final val LongT = 'l'
  private final val FloatT = 'f'
  private final val StringT = 's'
  private final val AggT = 'a'

  private final val tpes = Set(NullT, BoolT, LongT, FloatT, StringT, AggT)

  private final val controlChars = Set('.', '[', ']')
  private final val escapeChar = '\\'
  private final val escape = new CharEscape(controlChars, escapeChar)

  def flatten(node: Json): Seq[(String, String)] = {
    flatten(node, Seq.empty)
  }

  private def flatten(node: Json, prefix: Seq[PathElement]): Seq[(String, String)] = {
    node.fold(
      Seq(makePath(prefix, "null") -> "null"),
      b => Seq(makePath(prefix, "bool") -> b.toString),
      n => {
        val (kind, value) = n.toBigInt.fold("float" -> n.toBigDecimal.map(_.toString()).getOrElse(n.toDouble.toString))(bi => "long" -> bi.toString)
        Seq(makePath(prefix, kind) -> value)
      },
      s => Seq(makePath(prefix, "str") -> s),
      a => {
        val out = a.zipWithIndex.flatMap {
          case (element, idx) =>
            flatten(element, prefix :+ PathElement.Index(idx))
        }
        aggregate(prefix, "arr", out)
      },
      o => {
        val out = o.toIterable.flatMap {
          case (name, value) =>
            flatten(value, prefix :+ PathElement.ObjectName(name))
        }.toSeq
        aggregate(prefix, "obj", out)
      },
    )
  }

  private def aggregate(prefix: Seq[PathElement], kind: String, entries: Seq[(String, String)]): Seq[(String, String)] = {
    if (entries.nonEmpty) entries else Seq(makePath(prefix, "agg") -> kind)
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
    }.biSequence

    maybePaths.flatMap(inflateParsed)
  }

  @nowarn("msg=return statement uses an exception")
  private def parsePath(path: String): Either[List[UnpackFailure], (Seq[PathElement], Char)] = {
    val idx = path.lastIndexOf(':')
    if (idx < 0) {
      Left(Nil)
    } else {
      val (p, tpe) = path.splitAt(idx)

      if (tpe.length < 2) {
        Left(List(UnpackFailure.BadPathFormat(path)))
      } else {
        val rtpe = tpe.charAt(1)
        if (!tpes.contains(rtpe)) {
          Left(List(UnpackFailure.UnexpectedType(tpe.substring(1), path)))
        } else {

          val buf = new ArrayBuffer[PathElement]()
          var inEscape = false
          var start = 0

          var idx = 0
          var last: Either[List[UnpackFailure], Unit] = Right(())
          while (idx < p.length && last.isRight) {
            val c = p.charAt(idx)
            if (inEscape) {
              inEscape = false
            } else if (c == escapeChar) {
              inEscape = true
            } else if (c == '.') {
              last = addChunk(p, buf, start, idx)
              start = idx + 1
            }
            idx = idx + 1
          }

          last.flatMap {
            _ =>
              if (inEscape) Left(List(UnpackFailure.UnterminatedEscapeSequence(path)))
              else {
                val complete = if (start < p.length) addChunk(p, buf, start, p.length) else Right(())
                complete.map(_ => (buf.toVector, rtpe))
              }
          }
        }
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
        value.map(v => parse(v._2, v._3)).biSequence.map(Json.fromValues)
      case None =>
        val grouped2 = pairs.groupBy(_._1.head)

        if (grouped2.nonEmpty && grouped2.keys.forall(_.isInstanceOf[Index])) {
          grouped2.toSeq.sortBy(_._1.asInstanceOf[Index].idx).map(_._2).map(inflateParsedNext).biSequence.map(Json.fromValues)
        } else if (grouped2.keys.forall(_.isInstanceOf[ObjectName])) {
          grouped2
            .map {
              case (k, v) =>
                inflateParsedNext(v).map(field => escape.unescape(k.asInstanceOf[ObjectName].name) -> field)
            }.toSeq.biSequence.map(Json.fromFields)
        } else {
          Left(List(UnpackFailure.StructuralFailure(pairs)))
        }
    }
  }

  @inline private def drop(v: (Seq[PathElement], Char, String)): (Seq[PathElement], Char, String) = {
    v match {
      case (path, tpe, value) =>
        (path.drop(1), tpe, value)
    }
  }

  @inline private def inflateParsedNext(pairs: Seq[(Seq[PathElement], Char, String)]): Either[List[UnpackFailure], Json] = {
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
          case AggT =>
            value match {
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
