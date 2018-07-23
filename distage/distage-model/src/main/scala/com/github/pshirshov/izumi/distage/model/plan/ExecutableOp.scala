package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.util.Formattable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString


// TODO: typeclass?..
sealed trait ExecutableOp extends Formattable {
  def target: DIKey
  def origin: Option[Binding]

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


  final case class CreateSet(target: DIKey, tpe: TypeFull, members: Set[DIKey], origin: Option[Binding]) extends InstantiationOp {
    override def format: String = {
      val repr = FormattingUtils.doFormat(tpe.toString, members.map(_.toString).toSeq, "newset", ('[', ']'), ('{', '}')) // f"""$target := newset[$tpe]"""
      val pos = FormattingUtils.formatBindingPosition(origin)
      s"$target $pos := $repr"
    }
  }

  sealed trait WiringOp extends InstantiationOp {
    def wiring: Wiring
  }

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

  sealed trait ProxyOp extends ExecutableOp {}

  object ProxyOp {

    final case class MakeProxy(op: InstantiationOp, forwardRefs: Set[DIKey], origin: Option[Binding]) extends ProxyOp with InstantiationOp {
      override def target: DIKey = op.target

      override def format: String = {
        import IzString._
        val pos = FormattingUtils.formatBindingPosition(origin)
        s"""$target $pos := proxy(${forwardRefs.mkString(", ")}) {
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

}



