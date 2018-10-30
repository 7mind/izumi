package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.MakeProxy
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString

// TODO: typeclass?..
sealed trait ExecutableOp {
  def target: DIKey
  def origin: Option[Binding]
  def format: String

  override def toString: String = format
}


object ExecutableOp {

  sealed trait InstantiationOp extends ExecutableOp

  final case class ImportDependency(target: DIKey, references: Set[DIKey], origin: Option[Binding]) extends ExecutableOp {
    override def format: String = {
      val pos = FormattingUtils.formatBindingPosition(origin)
      s"$target $pos := import $target // required for ${references.mkString(" and ")}"
    }
  }


  final case class CreateSet(target: DIKey, tpe: SafeType, members: Set[DIKey], origin: Option[Binding]) extends InstantiationOp {
    override def format: String = {
      val repr = FormattingUtils.doFormat(tpe.toString, members.map(_.toString).toSeq, "newset", ('[', ']'), ('{', '}')) // f"""$target := newset[$tpe]"""
      val pos = FormattingUtils.formatBindingPosition(origin)
      s"$target $pos := $repr"
    }
  }

  sealed trait WiringOp extends InstantiationOp {
    def target: DIKey
    def wiring: Wiring
    def origin: Option[Binding]
  }

  sealed trait NewInstanceOp extends WiringOp {}
  object WiringOp {

    final case class InstantiateClass(target: DIKey, wiring: UnaryWiring.Constructor, origin: Option[Binding]) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring, origin)
    }

    final case class InstantiateTrait(target: DIKey, wiring: UnaryWiring.AbstractSymbol, origin: Option[Binding]) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring, origin)
    }

    final case class InstantiateFactory(target: DIKey, wiring: FactoryMethod, origin: Option[Binding]) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring, origin)
    }

    final case class CallProvider(target: DIKey, wiring: UnaryWiring.Function, origin: Option[Binding]) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring, origin)
    }

    final case class CallFactoryProvider(target: DIKey, wiring: FactoryFunction, origin: Option[Binding]) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring, origin)
    }

    final case class ReferenceInstance(target: DIKey, wiring: UnaryWiring.Instance, origin: Option[Binding]) extends WiringOp {
      override def format: String = {
        val pos = FormattingUtils.formatBindingPosition(origin)
        s"$target $pos := ${wiring.instance.getClass.getTypeName}#${wiring.instance.hashCode()}"
      }
    }

    final case class ReferenceKey(target: DIKey, wiring: UnaryWiring.Reference, origin: Option[Binding]) extends WiringOp {
      override def format: String = {
        val pos = FormattingUtils.formatBindingPosition(origin)
        s"$target $pos := ${wiring.key}"
      }
    }


  }

  sealed trait ProxyOp extends ExecutableOp

  object ProxyOp {

    final case class MakeProxy(op: InstantiationOp, forwardRefs: Set[DIKey], origin: Option[Binding], byNameAllowed: Boolean) extends ProxyOp {
      override def target: DIKey = op.target

      override def format: String = {
        import IzString._
        val pos = FormattingUtils.formatBindingPosition(origin)
        val kind = if (byNameAllowed) {
          "proxy.light"
        } else {
          "proxy.cogen"
        }

        s"""$target $pos := $kind(${forwardRefs.map(s => s"$s: deferred").mkString(", ")}) {
           |${op.toString.shift(2)}
           |}""".stripMargin
      }
    }

    final case class InitProxy(target: DIKey, dependencies: Set[DIKey], proxy: MakeProxy, origin: Option[Binding]) extends ProxyOp {
      override def format: String = {
        val pos = FormattingUtils.formatBindingPosition(origin)
        s"$target $pos -> init(${dependencies.mkString(", ")})"
      }
    }

  }

  @scala.annotation.tailrec
  def instanceType(op: ExecutableOp): SafeType = {
    op match {
      case w: WiringOp =>
        w.wiring match {
          case u: Wiring.UnaryWiring =>
            u.instanceType
          case _ =>
            w.target.tpe
        }
      case p: MakeProxy =>
        instanceType(p.op)
      case o =>
        o.target.tpe
    }
  }
}



