package izumi.thirdparty.internal.boopickle

/**
  * Specialized fast and cheap to initialize identity map for pickle state identifier map
  */
private[izumi] abstract class IdentMap {
  def apply(obj: AnyRef): Option[Int]

  def updated(obj: AnyRef): IdentMap
}

private[izumi] object IdentMap {
  def empty: IdentMap = EmptyIdentMap
}

private[izumi] object EmptyIdentMap extends IdentMap {
  override def apply(obj: AnyRef): Option[Int] = None

  override def updated(obj: AnyRef): IdentMap = new IdentMap1(obj)
}

private[boopickle] final class IdentMap1(o1: AnyRef) extends IdentMap {
  override def apply(obj: AnyRef): Option[Int] = {
    if (ReferenceEquality.eq(obj, o1))
      Some(2)
    else None
  }

  override def updated(obj: AnyRef): IdentMap = new IdentMap2(o1, obj)
}

private[boopickle] final class IdentMap2(o1: AnyRef, o2: AnyRef) extends IdentMap {
  override def apply(obj: AnyRef): Option[Int] = {
    if (ReferenceEquality.eq(obj, o1))
      Some(2)
    else if (ReferenceEquality.eq(obj, o2))
      Some(3)
    else None
  }

  override def updated(obj: AnyRef): IdentMap = new IdentMap3Plus(o1, o2, obj)
}

private[izumi] object IdentMap3Plus {

  private[boopickle] class Entry(val hash: Int, val obj: AnyRef, val idx: Int, var next: Entry)

}

private[boopickle] final class IdentMap3Plus(o1: AnyRef, o2: AnyRef, o3: AnyRef) extends IdentMap {
  import IdentMap3Plus.Entry

  var hashSize = 64
  val maxDepth = 1
  var hashTable = new Array[Entry](hashSize)
  // indices 0 (not used) and 1 (for null) are reserved
  var curIdx = 2

  // initialize with data
  updated(o1)
  updated(o2)
  updated(o3)

  @inline private def hashIdx(hash: Int) = {
    val h = scala.util.hashing.byteswap32(hash)
    ((h >> 16) ^ (h >> 8) ^ h) & (hashSize - 1)
  }

  override def apply(obj: AnyRef): Option[Int] = {
    val hash = ReferenceEquality.identityHashCode(obj)
    val tableIdx = hashIdx(hash)
    var e = hashTable(tableIdx)
    while ((e != null) && ReferenceEquality.ne(e.obj, obj)) e = e.next
    if (e == null)
      None
    else
      Some(e.idx)
  }

  override def updated(obj: AnyRef): IdentMap = {
    val hash = ReferenceEquality.identityHashCode(obj)
    val tableIdx = hashIdx(hash)
    hashTable(tableIdx) = new Entry(hash, obj, curIdx, hashTable(tableIdx))
    curIdx += 1
    // should we switch to next gear?
    if (curIdx > hashSize * maxDepth)
      resize()
    this
  }

  /**
    * Resizes the underlying hash table to make indexing fast as the number of entries grows
    */
  private def resize(): Unit = {
    val newSize = hashSize * 4
    val newTable = new Array[Entry](newSize)
    // copy old entries
    var i = hashSize - 1
    hashSize = newSize
    while (i >= 0) {
      var e = hashTable(i)
      while (e != null) {
        val tableIdx = hashIdx(e.hash)
        val n = e.next
        e.next = newTable(tableIdx)
        newTable(tableIdx) = e
        e = n
      }
      i -= 1
    }
    hashTable = newTable
  }
}
