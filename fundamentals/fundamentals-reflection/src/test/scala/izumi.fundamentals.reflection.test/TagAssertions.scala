package izumi.fundamentals.reflection.test

import izumi.fundamentals.reflection.macrortti.{LTag, LightTypeTag}
import org.scalatest.wordspec.AnyWordSpec

trait TagAssertions extends AnyWordSpec {
  def println(o: Any): Unit = info(o.toString)
  def println(o: LightTypeTag): Unit = info(o.ref.toString)

  def assertRepr(t: LightTypeTag, expected: String): Unit = {
    assert(t.toString == expected); ()
  }

  def assertDebugSame(t: LightTypeTag, expected: LightTypeTag): Unit = {
    assert(t.debug("assert") == expected.debug("assert")); ()
  }

  def assertSame(t: LightTypeTag, expected: LightTypeTag): Unit = {
    val clue = s"$t =?= $expected"
    info(clue)
    assert(t =:= expected, clue); ()
  }

  def assertDifferent(t: LightTypeTag, expected: LightTypeTag): Unit = {
    val clue = s"$t =!= $expected"
    info(clue)
    assert(!(t =:= expected), clue); ()
  }

  def assertChild(child: LightTypeTag, parent: LightTypeTag): Unit = {
    val clue = s"$child <?< $parent"
    info(clue)
    assert(child <:< parent, clue); ()
  }

  def assertNotChild(child: LightTypeTag, parent: LightTypeTag): Unit = {
    val clue = s"$child <!< $parent"
    info(clue)
    assert(!(child <:< parent), clue); ()
  }

  def assertCombine(outer: LightTypeTag, inner: Seq[LightTypeTag], expected: LightTypeTag): Unit = {
    val combined = outer.combine(inner: _*)
    val clue = s"($outer)•(${inner.mkString(",")}) => $combined =?= $expected"
    info(clue)
    assert(combined =:= expected, clue); ()
  }

  def assertCombine(outer: LightTypeTag, inner: LightTypeTag, expected: LightTypeTag): Unit = {
    val combined = outer.combine(inner)
    val clue = s"($outer)•($inner) => $combined =?= $expected"
    info(clue)
    assert(combined =:= expected, clue); ()
  }

  def assertCombineNonPos(outer: LightTypeTag, inner: Seq[Option[LightTypeTag]], expected: LightTypeTag): Unit = {
    val combined = outer.combineNonPos(inner: _*)
    info(s"($outer)•(${inner.mkString(",")}) => $combined =?= $expected")
    assert(combined =:= expected)
    ()
  }

  def literalLtt(s: String)(implicit l: LTag[s.type]): LightTypeTag = l.tag
}
