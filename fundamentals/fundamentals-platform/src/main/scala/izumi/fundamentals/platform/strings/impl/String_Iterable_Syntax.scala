package izumi.fundamentals.platform.strings.impl

import scala.collection.compat.*

final class String_Iterable_Syntax[A](private val s: IterableOnce[A]) extends AnyVal {
  def niceList(shift: String = " ", prefix: String = "- "): String = {
    val iterator = s.iterator
    if (iterator.nonEmpty) {
      val fullPrefix = s"\n$shift$prefix"
      iterator.mkString(fullPrefix, fullPrefix, "")
    } else {
      "ø"
    }
  }

  def niceMultilineList(prefix: String = "-"): String = {
    val iterator = s.iterator
    val sz = prefix.length
    val fst = s"$prefix" + " "
    val fwd = "\n" + " " * (sz + 1)
    if (iterator.nonEmpty) {
      iterator
        .map(_.toString)
        .map {
          s =>
            s.split('\n').mkString(fst, fwd, "")
        }
        .mkString("\n")
    } else {
      "ø"
    }
  }
}
