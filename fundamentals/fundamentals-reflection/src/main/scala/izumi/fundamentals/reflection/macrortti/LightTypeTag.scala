package izumi.fundamentals.reflection.macrortti

import java.nio.ByteBuffer

import boopickle.Default.Pickler
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.reflection.TrivialMacroLogger
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ParsedLightTypeTag.SubtypeDBs
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.{AbstractReference, AppliedReference, NameReference}

abstract class LightTypeTag
(
  bases: () => Map[AbstractReference, Set[AbstractReference]],
  db: () => Map[NameReference, Set[NameReference]],
) extends Serializable {

  def ref: LightTypeTagRef
  protected[macrortti] lazy val basesdb: Map[AbstractReference, Set[AbstractReference]] = bases()
  protected[macrortti] lazy val idb: Map[NameReference, Set[NameReference]] = db()

  @inline final def <:<(maybeParent: LightTypeTag): Boolean = {
    new LightTypeTagInheritance(this, maybeParent).isChild()
  }

  @inline final def =:=(other: LightTypeTag): Boolean = {
    this == other
  }

  def combine(o: LightTypeTag*): LightTypeTag = {

    def mergedInhDb: Map[NameReference, Set[NameReference]] =
      o.foldLeft(idb) {
        case (acc, v) =>
          LightTypeTag.mergeIDBs(acc, v.idb)
      }

    def mergedBases: Map[AbstractReference, Set[AbstractReference]] = {
      o.foldLeft(basesdb) {
        case (acc, v) =>
          LightTypeTag.mergeIDBs(acc, v.basesdb)
      }
    }

    LightTypeTag(ref.combine(o.map(_.ref)), mergedBases, mergedInhDb)
  }

  def combineNonPos(o: Option[LightTypeTag]*): LightTypeTag = {

    def mergedInhDb: Map[NameReference, Set[NameReference]] = {
      o.foldLeft(idb) {
        case (acc, v) =>
          LightTypeTag.mergeIDBs(acc, v.map(_.idb).getOrElse(Map.empty))
      }
    }

    def mergedBases: Map[AbstractReference, Set[AbstractReference]] = {
      o.foldLeft(basesdb) {
        case (acc, v) =>
          LightTypeTag.mergeIDBs(acc, v.map(_.basesdb).getOrElse(Map.empty))
      }
    }

    LightTypeTag(ref.combineNonPos(o.map(_.map(_.ref))), mergedBases, mergedInhDb)
  }

  override def toString: String = {
    ref.toString
  }

  /** Fully-qualified printing of a type, use [[toString]] for a printing that omits package names */
  def repr: String = {
    import izumi.fundamentals.reflection.macrortti.LTTRenderables.Long._
    ref.render()
  }

  override def equals(other: Any): Boolean = {
    other match {
      case that: LightTypeTag =>
        ref == that.ref
      case _ => false
    }
  }

  override def hashCode(): Int = {
    val state = Seq(ref)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object LightTypeTag {
  @inline def apply(ref0: LightTypeTagRef, bases: => Map[AbstractReference, Set[AbstractReference]], db: => Map[NameReference, Set[NameReference]]): LightTypeTag = {
    new LightTypeTag(() => bases, () => db) {
      override final val ref: LightTypeTagRef = ref0
    }
  }

  final class ParsedLightTypeTag(
                                  private val refString: String,
                                  bases: () => Map[AbstractReference, Set[AbstractReference]],
                                  db: () => Map[NameReference, Set[NameReference]],
                                ) extends LightTypeTag(bases, db) {
    override lazy val ref: LightTypeTagRef = {
      lttRefSerializer.unpickle(ByteBuffer.wrap(refString.getBytes("ISO-8859-1")))
    }

    override def equals(other: Any): Boolean = {
      other match {
        case that: ParsedLightTypeTag if refString == that.refString =>
          true
        case _ =>
          super.equals(other)
      }
    }
  }

  object ParsedLightTypeTag {
    final case class SubtypeDBs(bases: Map[AbstractReference, Set[AbstractReference]], idb: Map[NameReference, Set[NameReference]])
  }

  // parse lazy ParsedLightTypeTag
  def parse[T](refString: String, basesString: String): LightTypeTag = {
    lazy val shared = {
      subtypeDBsSerializer.unpickle(ByteBuffer.wrap(basesString.getBytes("ISO-8859-1")))
    }

    new ParsedLightTypeTag(refString, () => shared.bases, () => shared.idb)
  }

  val (lttRefSerializer: Pickler[LightTypeTagRef], subtypeDBsSerializer: Pickler[SubtypeDBs]) = {
    import boopickle.Default._

    implicit lazy val appliedRefSerializer: Pickler[AppliedReference] = generatePickler[AppliedReference]
    implicit lazy val nameRefSerializer: Pickler[NameReference] = generatePickler[NameReference]
    implicit lazy val abstractRefSerializer: Pickler[AbstractReference] = generatePickler[AbstractReference]

    implicit lazy val refSerializer: Pickler[LightTypeTagRef] = generatePickler[LightTypeTagRef]
    implicit lazy val dbsSerializer: Pickler[SubtypeDBs] = generatePickler[SubtypeDBs]

    // false positive unused warnings
    appliedRefSerializer.discard(); nameRefSerializer.discard(); abstractRefSerializer.discard()

    (refSerializer, dbsSerializer)
  }

  final val loggerId = TrivialMacroLogger.id("rtti")

  private[izumi] object ReflectionLock

  private[macrortti] def mergeIDBs[T](self: Map[T, Set[T]], other: Map[T, Set[T]]): Map[T, Set[T]] = {
    import izumi.fundamentals.collections.IzCollections._

    val both = self.toSeq ++ other.toSeq
    both.toMultimap.mapValues(_.flatten).toMap
  }

}
