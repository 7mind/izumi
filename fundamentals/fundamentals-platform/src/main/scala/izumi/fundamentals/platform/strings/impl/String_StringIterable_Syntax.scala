package izumi.fundamentals.platform.strings.impl

final class String_StringIterable_Syntax[A](private val ss: Iterable[String]) extends AnyVal {
  def smartStrip(): Iterable[String] = {
    val toRemove = (List(Int.MaxValue) ++ ss.filterNot(_.trim.isEmpty).map(_.takeWhile(_.isSpaceChar).length)).min
    if (ss.nonEmpty && toRemove > 0) {
      ss.map {
        s =>
          if (s.length >= toRemove && s.substring(0, toRemove).forall(_.isSpaceChar)) {
            s.substring(toRemove, s.length)
          } else {
            s
          }
      }
    } else {
      ss
    }
  }
}
