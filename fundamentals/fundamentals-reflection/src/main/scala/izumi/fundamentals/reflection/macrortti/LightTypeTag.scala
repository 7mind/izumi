package izumi.fundamentals.reflection.macrortti

import java.nio.ByteBuffer

import boopickle.{DefaultBasic, Pickler}
import izumi.fundamentals.reflection.TrivialMacroLogger
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.{AbstractReference, AppliedReference, FullReference, NameReference, TypeParam}

final class LightTypeTag
(
  val ref: LightTypeTagRef,
  bases: () => Map[AbstractReference, Set[AbstractReference]],
  db: () => Map[NameReference, Set[NameReference]],
) extends Serializable {

  protected[macrortti] lazy val basesdb: Map[AbstractReference, Set[AbstractReference]] = bases()
  protected[macrortti] lazy val idb: Map[NameReference, Set[NameReference]] = db()

  @inline def <:<(maybeParent: LightTypeTag): Boolean = {
    new LightTypeTagInheritance(this, maybeParent).isChild()
  }

  @inline def =:=(other: LightTypeTag): Boolean = {
    this == other
  }

  def combine(o: LightTypeTag*): LightTypeTag = {

    val mergedInhDb: () => Map[NameReference, Set[NameReference]] = () => {
      o.foldLeft(idb) {
        case (acc, v) =>
          LightTypeTag.mergeIDBs(acc, v.idb)
      }
    }

    val mergedBases: () => Map[AbstractReference, Set[AbstractReference]] = () => {
      o.foldLeft(basesdb) {
        case (acc, v) =>
          LightTypeTag.mergeIDBs(acc, v.basesdb)
      }
    }

    new LightTypeTag(ref.combine(o.map(_.ref)), mergedBases, mergedInhDb)
  }

  def combineNonPos(o: Option[LightTypeTag]*): LightTypeTag = {

    val mergedInhDb: () => Map[NameReference, Set[NameReference]] = () => {
      o.foldLeft(idb) {
        case (acc, v) =>
          LightTypeTag.mergeIDBs(acc, v.map(_.idb).getOrElse(Map.empty))
      }
    }

    val mergedBases: () => Map[AbstractReference, Set[AbstractReference]] = () => {
      o.foldLeft(basesdb) {
        case (acc, v) =>
          LightTypeTag.mergeIDBs(acc, v.map(_.basesdb).getOrElse(Map.empty))
      }
    }

    new LightTypeTag(ref.combineNonPos(o.map(_.map(_.ref))), mergedBases, mergedInhDb)
  }

  override def toString: String = {
    //    import izumi.fundamentals.reflection.macrortti.LTTRenderables.Long._
    //    t.render()
    ref.toString
  }

  def repr: String = {
    import izumi.fundamentals.reflection.macrortti.LTTRenderables.Long._
    ref.render()
  }

  override def equals(other: Any): Boolean = other match {
    case that: LightTypeTag =>
      ref == that.ref
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(ref)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object LightTypeTag {
  def apply(ref: LightTypeTagRef, bases: => Map[AbstractReference, Set[AbstractReference]], db: => Map[NameReference, Set[NameReference]]): LightTypeTag = {
    new LightTypeTag(ref, () => bases, () => db)
  }


  def parse[T](s: String): LightTypeTag = {
    val bytes = s.getBytes("ISO-8859-1")
    binarySerializer.unpickle(ByteBuffer.wrap(bytes, 0, bytes.length))
//    new ObjectInputStream(new ByteArrayInputStream(s.getBytes("ISO-8859-1"))).readObject().asInstanceOf[LightTypeTag]
  }

  implicit val binarySerializer: Pickler[LightTypeTag] = {
    import boopickle.Default._
    implicit val serializer6 = generatePickler[AppliedReference]
    implicit val serializer4 = generatePickler[NameReference]
    implicit val serializer5 = generatePickler[AbstractReference]
    implicit val refSerializer = generatePickler[LightTypeTagRef]

    implicit val z = generatePickler[(
      LightTypeTagRef,
      Map[AbstractReference, Set[AbstractReference]],
      Map[NameReference, Set[NameReference]],
    )]

    DefaultBasic.transformPickler[LightTypeTag,
      (
        LightTypeTagRef,
        Map[AbstractReference, Set[AbstractReference]],
        Map[NameReference, Set[NameReference]],
      )
    ] {
      case (a, b, c) => apply(a, b, c)
    } {
      l => (l.ref, l.basesdb, l.idb)
    }
  }

  final val loggerId = TrivialMacroLogger.id("rtti")

  object ReflectionLock

  protected[macrortti] def mergeIDBs[T](self: Map[T, Set[T]], other: Map[T, Set[T]]): Map[T, Set[T]] = {
    import izumi.fundamentals.collections.IzCollections._

    val both = self.toSeq ++ other.toSeq
    both.toMultimap.mapValues(_.flatten).toMap
  }

}
