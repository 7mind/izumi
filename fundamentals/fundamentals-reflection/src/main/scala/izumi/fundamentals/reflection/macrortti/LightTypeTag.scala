package izumi.fundamentals.reflection.macrortti

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ParsedLightTypeTag.SubtypeDBs
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.SymName.{SymTermName, SymTypeName}
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.{AbstractReference, AppliedNamedReference, AppliedReference, NameReference, SymName}
import izumi.thirdparty.internal.boopickle.Default.Pickler

/**
  * Extracts internal databases from [[LightTypeTag]].
  * Should be not used under normal circumstances.
  */
case class LightTypeTagUnpacker(tag: LightTypeTag) {
  def bases: Map[AbstractReference, Set[AbstractReference]] = tag.basesdb
  def inheritance: Map[NameReference, Set[NameReference]] = tag.idb
}

abstract class LightTypeTag
(
  bases: () => Map[AbstractReference, Set[AbstractReference]],
  inheritanceDb: () => Map[NameReference, Set[NameReference]]
) extends Serializable {

  def ref: LightTypeTagRef
  protected[macrortti] lazy val basesdb: Map[AbstractReference, Set[AbstractReference]] = bases()
  protected[macrortti] lazy val idb: Map[NameReference, Set[NameReference]] = inheritanceDb()

  @inline final def <:<(maybeParent: LightTypeTag): Boolean = {
    new LightTypeTagInheritance(this, maybeParent).isChild()
  }

  @inline final def =:=(other: LightTypeTag): Boolean = {
    this == other
  }

  final def decompose: Set[LightTypeTag] = {
    ref match {
      case LightTypeTagRef.IntersectionReference(refs) =>
        refs.map(r => LightTypeTag.apply(r, bases(), inheritanceDb()))
      case _ =>
        Set(this)
    }
  }

  /**
    * Parameterize this type tag with `args` if it describes an unapplied type lambda
    *
    * If there are less `args` given than this type takes parameters, it will remain a type
    * lambda taking remaining arguments:
    *
    * {{{
    *   F[?, ?, ?].combine(A, B) = F[A, B, ?]
    * }}}
    */
  def combine(args: LightTypeTag*): LightTypeTag = {
    val argRefs = args.map(_.ref)
    val appliedBases = basesdb.map {
      case (self: LightTypeTagRef.Lambda, parents) =>
        self.combine(argRefs) -> parents.map {
          case l: LightTypeTagRef.Lambda =>
            l.combine(argRefs)
          case o =>
            val context = self.input.map(_.name).zip(argRefs.collect {case a: AbstractReference => a}).toMap
            val out = new RuntimeAPI.Rewriter(context).replaceRefs(o)
            out
        }
      case o => o
    }

    def mergedBasesDB = LightTypeTag.mergeIDBs(appliedBases, args.iterator.map(_.basesdb))
    def mergedInheritanceDb = LightTypeTag.mergeIDBs(idb, args.iterator.map(_.idb))

    LightTypeTag(ref.combine(argRefs), mergedBasesDB, mergedInheritanceDb)
  }

  /**
    * Parameterize this type tag with `args` if it describes an unapplied type lambda
    *
    * The resulting type lambda will take parameters in places where `args` was None:
    *
    * {{{
    *   F[?, ?, ?].combine(Some(A), None, Some(C)) = F[A, ?, C]
    * }}}
    */
  def combineNonPos(args: Option[LightTypeTag]*): LightTypeTag = {
    val argRefs = args.map(_.map(_.ref))
    val appliedBases = basesdb ++ basesdb.map {
      case (self: LightTypeTagRef.Lambda, parents) =>
        self.combineNonPos(argRefs) -> parents.map {
          case l: LightTypeTagRef.Lambda =>
            l.combineNonPos(argRefs)
          case o => o
        }
      case o => o
    }

    def mergedBasesDB = LightTypeTag.mergeIDBs(appliedBases, args.iterator.map(_.map(_.basesdb).getOrElse(Map.empty)))
    def mergedInheritanceDb = LightTypeTag.mergeIDBs(idb, args.iterator.map(_.map(_.idb).getOrElse(Map.empty)))

    LightTypeTag(ref.combineNonPos(argRefs), mergedBasesDB, mergedInheritanceDb)
  }

  /**
    * Strip all args from type tag of parameterized type and its supertypes
    * Useful for very rough type-constructor / class-only comparisons.
    *
    * NOTE: This DOES NOT RESTORE TYPE CONSTRUCTOR/LAMBDA and is
    *       NOT equivalent to .typeConstructor call in scala-reflect
    *       - You won't be able to call [[combine]] on result type
    *       and partially applied types will not work correctly
    */
  def withoutArgs: LightTypeTag = {
    LightTypeTag(ref.withoutArgs, basesdb.mapValues(_.map(_.withoutArgs)).toMap, idb)
  }

  /**
    * Extract arguments applied to this type constructor
    */
  def typeArgs: List[LightTypeTag] = {
    ref.typeArgs.map(LightTypeTag(_, basesdb, idb))
  }

  /** Render to string, omitting package names */
  override def toString: String = {
    ref.toString
  }

  /** Fully-qualified rendering of a type, including packages and prefix types.
    * Use [[toString]] for a rendering that omits package names */
  def repr: String = {
    import izumi.fundamentals.reflection.macrortti.LTTRenderables.Long._
    ref.render()
  }

  /** Short class or type-constructor name of this type, without package or prefix names */
  def shortName: String = {
    ref.shortName
  }

  /** Class or type-constructor name of this type, WITH package name, but without prefix names */
  def longName: String = {
    ref.longName
  }

  /** Print internal structures state */
  def debug(name: String = ""): String = {
    import izumi.fundamentals.platform.strings.IzString._
      s"""⚙️ $name: ${this.toString}
         |⚡️bases: ${basesdb.mapValues(_.niceList(prefix = "* ").shift(2)).niceList()}
         |⚡️inheritance: ${idb.mapValues(_.niceList(prefix = "* ").shift(2)).niceList()}
         |⚙️ end $name""".stripMargin
  }

  override def equals(other: Any): Boolean = {
    other match {
      case that: LightTypeTag =>
        ref == that.ref
      case _ => false
    }
  }

  override def hashCode(): Int = hashcode

  private[this] lazy val hashcode: Int = {
    ref.hashCode() * 31
  }
}

object LightTypeTag {
  @inline def apply(ref0: LightTypeTagRef, bases: => Map[AbstractReference, Set[AbstractReference]], db: => Map[NameReference, Set[NameReference]]): LightTypeTag = {
    new LightTypeTag(() => bases, () => db) {
      override final val ref: LightTypeTagRef = ref0
    }
  }

  def refinedType(intersection: List[LightTypeTag], structure: LightTypeTag): LightTypeTag = {
    def mergedBasesDB = LightTypeTag.mergeIDBs(structure.basesdb, intersection.iterator.map(_.basesdb))
    def mergedInheritanceDb = LightTypeTag.mergeIDBs(structure.idb, intersection.iterator.map(_.idb))

    val parts = intersection.iterator.collect { case l if l.ref.isInstanceOf[AppliedNamedReference] => l.ref.asInstanceOf[AppliedNamedReference] }.toSet
    val intersectionRef = LightTypeTagRef.maybeIntersection(parts)

    val ref = structure.ref match {
      case LightTypeTagRef.Refinement(_, decls) if decls.nonEmpty =>
        LightTypeTagRef.Refinement(intersectionRef, decls)
      case _ =>
        intersectionRef
    }

    LightTypeTag(ref, mergedBasesDB, mergedInheritanceDb)
  }

  def parse[T](hashCode: Int, refString: String, basesString: String, @unused version: Int): LightTypeTag = {
    lazy val shared = {
      subtypeDBsSerializer.unpickle(ByteBuffer.wrap(basesString.getBytes(StandardCharsets.ISO_8859_1)))
    }

    new ParsedLightTypeTag(hashCode, refString, () => shared.bases, () => shared.idb)
  }

  final class ParsedLightTypeTag(
                                  override val hashCode: Int,
                                  private val refString: String,
                                  bases: () => Map[AbstractReference, Set[AbstractReference]],
                                  db: () => Map[NameReference, Set[NameReference]]
                                ) extends LightTypeTag(bases, db) {
    override lazy val ref: LightTypeTagRef = {
      lttRefSerializer.unpickle(ByteBuffer.wrap(refString.getBytes(StandardCharsets.ISO_8859_1)))
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

  private[macrortti] val (lttRefSerializer: Pickler[LightTypeTagRef], subtypeDBsSerializer: Pickler[SubtypeDBs]) = {
    import izumi.thirdparty.internal.boopickle.Default._

    implicit lazy val symTypeName: Pickler[SymTypeName] = generatePickler[SymTypeName]
    implicit lazy val symTermName: Pickler[SymTermName] = generatePickler[SymTermName]
    implicit lazy val symName: Pickler[SymName] = generatePickler[SymName]
    implicit lazy val appliedRefSerializer: Pickler[AppliedReference] = generatePickler[AppliedReference]
    implicit lazy val nameRefSerializer: Pickler[NameReference] = generatePickler[NameReference]
    implicit lazy val abstractRefSerializer: Pickler[AbstractReference] = generatePickler[AbstractReference]

    implicit lazy val refSerializer: Pickler[LightTypeTagRef] = generatePickler[LightTypeTagRef]
    implicit lazy val dbsSerializer: Pickler[SubtypeDBs] = generatePickler[SubtypeDBs]

    // false positive unused warnings
    symTypeName.discard()
    symTermName.discard()
    symName.discard()
    appliedRefSerializer.discard()
    nameRefSerializer.discard()
    abstractRefSerializer.discard()

    (refSerializer, dbsSerializer)
  }

  private[macrortti] def mergeIDBs[T](self: Map[T, Set[T]], other: Map[T, Set[T]]): Map[T, Set[T]] = {
    import izumi.fundamentals.collections.IzCollections._

    val both = self.toSeq ++ other.toSeq
    both.toMultimap.map {
      case (k, v) =>
        (k, v.flatten.filterNot(_ == k))
    }
  }

  private[macrortti] def mergeIDBs[T](self: Map[T, Set[T]], others: Iterator[Map[T, Set[T]]]): Map[T, Set[T]] = {
    others.foldLeft(self)(mergeIDBs[T])
  }

}
