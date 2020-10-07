package izumi.fundamentals.collections

class OrderedSetShim[A](ordered: Seq[A]) extends Set[A] {
  private val realSet = ordered.toSet

  override def `+`(elem: A): Set[A] = realSet + elem

  override def `-`(elem: A): Set[A] = realSet - elem

  override def contains(elem: A): Boolean = realSet.contains(elem)

  override def iterator: Iterator[A] = ordered.iterator
}
