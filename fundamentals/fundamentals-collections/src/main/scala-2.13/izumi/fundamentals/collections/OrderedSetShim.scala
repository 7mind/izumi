package izumi.fundamentals.collections

class OrderedSetShim[A](ordered: Seq[A]) extends Set[A] {
  private val realSet: Set[A] = ordered.toSet

  override def incl(elem: A): Set[A] = realSet.incl(elem)

  override def excl(elem: A): Set[A] = realSet.excl(elem)

  override def contains(elem: A): Boolean = realSet.contains(elem)

  override def iterator: Iterator[A] = ordered.iterator
}
