package izumi.fundamentals.collections

class OrderedSetShim[A](val ordered: Seq[A]) extends Set[A] {
  private lazy val realSet: Set[A] = ordered.toSet

  override def incl(elem: A): Set[A] = realSet.incl(elem)

  override def excl(elem: A): Set[A] = realSet.excl(elem)

  override def contains(elem: A): Boolean = realSet.contains(elem)

  override def iterator: Iterator[A] = ordered.iterator
}
