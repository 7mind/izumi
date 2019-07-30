package com.github.pshirshov.izumi.fundamentals.reflection.macrortti

import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LightTypeTag.{AbstractReference, NameReference}

class FLTT(
            val t: LightTypeTag,
            bases: () => Map[AbstractReference, Set[AbstractReference]],
            db: () => Map[NameReference, Set[NameReference]],
          ) {

  protected[macrortti] lazy val basesdb: Map[AbstractReference, Set[AbstractReference]] = bases()
  protected[macrortti] lazy val idb: Map[NameReference, Set[NameReference]] = db()

  def <:<(maybeParent: FLTT): Boolean = {
    new LightTypeTagInheritance(this, maybeParent).isChild()
  }

  def =:=(other: FLTT): Boolean = {
    this == other
  }


  def combine(o: FLTT*): FLTT = {

    val mergedInhDb: () => Map[NameReference, Set[NameReference]] = () => {
      o.foldLeft(idb) {
        case (acc, v) =>
          FLTT.mergeIDBs(acc, v.idb)
      }
    }

    val mergedBases: () => Map[AbstractReference, Set[AbstractReference]] = () => {
      o.foldLeft(basesdb) {
        case (acc, v) =>
          FLTT.mergeIDBs(acc, v.basesdb)
      }
    }

    new FLTT(t.combine(o.map(_.t)), mergedBases, mergedInhDb)
  }

  def combineNonPos(o: Option[FLTT]*): FLTT = {

    val mergedInhDb: () => Map[NameReference, Set[NameReference]] = () => {
      o.foldLeft(idb) {
        case (acc, v) =>
          FLTT.mergeIDBs(acc, v.map(_.idb).getOrElse(Map.empty))
      }
    }

    val mergedBases: () => Map[AbstractReference, Set[AbstractReference]] = () => {
      o.foldLeft(basesdb) {
        case (acc, v) =>
          FLTT.mergeIDBs(acc, v.map(_.basesdb).getOrElse(Map.empty))
      }
    }

    new FLTT(t.combineNonPos(o.map(_.map(_.t))), mergedBases, mergedInhDb)
  }

  override def toString: String = {
    //    import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LTTRenderables.Long._
    //    t.render()
    t.toString
  }


  def repr: String = {
    import com.github.pshirshov.izumi.fundamentals.reflection.macrortti.LTTRenderables.Long._
    t.render()
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[FLTT]

  override def equals(other: Any): Boolean = other match {
    case that: FLTT =>
      (that canEqual this) &&
        t == that.t
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(t)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object FLTT {

  import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._

  protected[macrortti] def mergeIDBs[T](self: Map[T, Set[T]], other: Map[T, Set[T]]): Map[T, Set[T]] = {

    val both = self.toSeq ++ other.toSeq
    both.toMultimap.mapValues(_.flatten)

  }

}
