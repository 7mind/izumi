package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisMember
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr

import scala.language.implicitConversions

//import scala.language.implicitConversions
//sealed trait BindingTag extends Any
//
//object BindingTag {
//
//  final case object Untagged extends BindingTag {
//    override def toString: String = "<*>"
//  }
//
//  final case object TSingleton extends BindingTag {
//    override def toString: String = "<singleton>"
//  }
//
//  final case object TSet extends BindingTag {
//    override def toString: String = "<set>"
//  }
//
//  final case object TSetElement extends BindingTag {
//    override def toString: String = "<set-element>"
//  }
//
//  final case class StringTag(value: String) extends AnyVal with BindingTag {
//    override def toString: String = value
//  }
//
//  implicit def fromString(tag: String): BindingTag = StringTag(tag)
//
//  implicit def fromStrings(tags: Seq[String]): Seq[BindingTag] = Seq(tags.map(BindingTag.apply): _*)
//
//  final val untaggedTags: Set[BindingTag] = Set(Untagged)
//
//  def apply(tag: String): BindingTag = StringTag(tag)
//
//  def apply(tags: String*): Set[BindingTag] = Set(tags.map(BindingTag.apply): _*)
//
//  def fromSeq(tags: Seq[String]): Set[BindingTag] = apply(tags: _*)
//

//}

//sealed trait Parameter
//
//object Parameter {
//
//  case class AxisDef(axis: Axis[AxisMember], choice: AxisMember) extends Parameter
//
//  case class OverrideDef(key: Key, choice: String) extends Parameter
//
//}

sealed trait BindingTag

object BindingTag {

  case class AxisTag(choice: AxisMember) extends BindingTag

  case class StringTag(tag: String) extends BindingTag

  implicit def apply(tag: AxisMember): BindingTag = AxisTag(tag)

  implicit def apply(tag: String): BindingTag = StringTag(tag)

  object Expressions extends TagExpr.For[BindingTag] {

    implicit class C(val sc: StringContext) {
      def t(args: Any*): Expr = Has(apply(sc.s(args: _*)))
    }

  }

}

sealed trait AxisBase {
  def name: String

  override def toString: String = s"$name"
  implicit def self: AxisBase = this

}

trait Axis[+MM <: AxisMember] extends AxisBase {

}

object Axis {

  trait AxisMember {
    def axis: AxisBase

    def activatedBy: Set[AxisMember] = Set.empty

    def deactivatedBy: Set[AxisMember] = Set.empty

    def id: String = {
      val n = getClass.getName.toLowerCase
      n.split('$').last
    }

    override def toString: String = s"$axis:$id"
  }

}


abstract class EnvAxis()(implicit val axis: AxisBase) extends AxisMember

object EnvAxis extends Axis[EnvAxis] {
  override def name: String = "env"

  case object Production extends EnvAxis

  case object Mock extends EnvAxis
}
