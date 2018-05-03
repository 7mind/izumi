package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.util.Formattable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString

// TODO: typeclass?..
sealed trait ExecutableOp extends Formattable {
  def target: RuntimeDIUniverse.DIKey

  override def toString: String = format
}


object ExecutableOp {

  sealed trait InstantiationOp extends ExecutableOp

  final case class ImportDependency(target: RuntimeDIUniverse.DIKey, references: Set[RuntimeDIUniverse.DIKey]) extends ExecutableOp {
    override def format: String = f"""$target := import $target // required for ${references.mkString(" and ")}"""
  }

  final case class CustomOp(target: RuntimeDIUniverse.DIKey, data: CustomWiring) extends InstantiationOp {
    override def format: String = f"""$target := custom($target)"""
  }

  sealed trait SetOp extends ExecutableOp

  object SetOp {

    final case class CreateSet(target: RuntimeDIUniverse.DIKey, tpe: RuntimeDIUniverse.TypeFull) extends SetOp {
      override def format: String = f"""$target := newset[$tpe]"""
    }

    final case class AddToSet(target: RuntimeDIUniverse.DIKey, element: RuntimeDIUniverse.DIKey) extends SetOp with InstantiationOp {
      override def format: String = f"""$target += $element"""
    }

  }

  sealed trait WiringOp extends InstantiationOp {
    def wiring: RuntimeDIUniverse.Wiring
  }

  object WiringOp {

    final case class InstantiateClass(target: RuntimeDIUniverse.DIKey, wiring: UnaryWiring.Constructor) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    final case class InstantiateTrait(target: RuntimeDIUniverse.DIKey, wiring: UnaryWiring.Abstract) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    final case class InstantiateFactory(target: RuntimeDIUniverse.DIKey, wiring: FactoryMethod) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    final case class CallProvider(target: RuntimeDIUniverse.DIKey, wiring: UnaryWiring.Function) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    final case class ReferenceInstance(target: RuntimeDIUniverse.DIKey, wiring: UnaryWiring.Instance) extends WiringOp {
      override def format: String = {
        s"$target := ${wiring.instance.getClass.getTypeName}#${wiring.instance.hashCode()}"
      }
    }

  }

  sealed trait ProxyOp extends ExecutableOp {}

  object ProxyOp {

    final case class MakeProxy(op: InstantiationOp, forwardRefs: Set[RuntimeDIUniverse.DIKey]) extends ProxyOp with InstantiationOp {
      override def target: RuntimeDIUniverse.DIKey = op.target

      override def format: String = {
        import IzString._
        f"""$target := proxy($forwardRefs) {
           |${op.toString.shift(2)}
           |}""".stripMargin
      }
    }

    final case class InitProxy(target: RuntimeDIUniverse.DIKey, dependencies: Set[RuntimeDIUniverse.DIKey], proxy: MakeProxy) extends ProxyOp {
      override def format: String = f"""$target -> init(${dependencies.mkString(", ")})"""
    }

  }

}



