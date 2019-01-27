package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.fundamentals.tags.TagExpr

import scala.language.implicitConversions

sealed trait BindingTag extends Any

object BindingTag {

  final case object Untagged extends BindingTag {
    override def toString: String = "<*>"
  }

  final case object TSingleton extends BindingTag {
    override def toString: String = "<singleton>"
  }

  final case object TResource extends BindingTag {
    override def toString: String = "<resource>"
  }

  final case object TSet extends BindingTag {
    override def toString: String = "<set>"
  }

  final case object TSetElement extends BindingTag {
    override def toString: String = "<set-element>"
  }

  final case class StringTag(value: String) extends AnyVal with BindingTag {
    override def toString: String = value
  }

  implicit def fromString(tag: String): BindingTag = StringTag(tag)

  implicit def fromStrings(tags: Seq[String]): Seq[BindingTag] = Seq(tags.map(BindingTag.apply): _*)

  final val untaggedTags: Set[BindingTag] = Set(Untagged)

  def apply(tag: String): BindingTag = StringTag(tag)

  def apply(tags: String*): Set[BindingTag] = Set(tags.map(BindingTag.apply): _*)

  def fromSeq(tags: Seq[String]): Set[BindingTag] = apply(tags: _*)

  object Expressions extends TagExpr.For[BindingTag] {

    implicit class C(val sc: StringContext) {
      def t(args: Any*): Expr = Has(apply(sc.s(args: _*)))
    }

  }
}
