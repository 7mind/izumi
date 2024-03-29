package izumi.fundamentals.collections

class OrderedSetShim[A](val ordered: Seq[A]) extends Set[A] {
  private lazy val realSet = ordered.toSet

  override def `+`(elem: A): Set[A] = realSet + elem

  override def `-`(elem: A): Set[A] = realSet - elem

  override def contains(elem: A): Boolean = realSet.contains(elem)

  override def iterator: Iterator[A] = ordered.iterator
}
