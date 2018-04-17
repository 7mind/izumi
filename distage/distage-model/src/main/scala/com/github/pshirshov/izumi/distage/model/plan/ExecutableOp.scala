package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.util.Formattable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString

// TODO: typeclass?..
sealed trait ExecutableOp extends Formattable {
  def target: RuntimeUniverse.DIKey

  override def toString: String = format
}


object ExecutableOp {

  sealed trait InstantiationOp extends ExecutableOp

  case class ImportDependency(target: RuntimeUniverse.DIKey, references: Set[RuntimeUniverse.DIKey]) extends ExecutableOp {
    override def format: String = f"""$target := import $target // required for $references"""
  }

  case class CustomOp(target: RuntimeUniverse.DIKey, data: CustomWiring) extends InstantiationOp {
    override def format: String = f"""$target := custom($target)"""
  }

  sealed trait SetOp extends ExecutableOp

  object SetOp {

    case class CreateSet(target: RuntimeUniverse.DIKey, tpe: RuntimeUniverse.TypeFull) extends SetOp {
      override def format: String = f"""$target := newset[$tpe]"""
    }

    case class AddToSet(target: RuntimeUniverse.DIKey, element: RuntimeUniverse.DIKey) extends SetOp with InstantiationOp {
      override def format: String = f"""$target += $element"""
    }

  }

  sealed trait WiringOp extends InstantiationOp {
    def wiring: RuntimeUniverse.Wiring
  }

  object WiringOp {

    case class InstantiateClass(target: RuntimeUniverse.DIKey, wiring: UnaryWiring.Constructor) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    case class InstantiateTrait(target: RuntimeUniverse.DIKey, wiring: UnaryWiring.Abstract) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    case class InstantiateFactory(target: RuntimeUniverse.DIKey, wiring: FactoryMethod) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    case class CallProvider(target: RuntimeUniverse.DIKey, wiring: UnaryWiring.Function) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    case class ReferenceInstance(target: RuntimeUniverse.DIKey, wiring: UnaryWiring.Instance) extends WiringOp {
      override def format: String = {
        s"$target := ${wiring.instance.getClass.getTypeName}#${wiring.instance.hashCode()}"
      }
    }

  }

  sealed trait ProxyOp extends ExecutableOp {}

  object ProxyOp {

    case class MakeProxy(op: InstantiationOp, forwardRefs: Set[RuntimeUniverse.DIKey]) extends ProxyOp with InstantiationOp {
      override def target: RuntimeUniverse.DIKey = op.target

      override def format: String = {
        import IzString._
        f"""$target := proxy($forwardRefs) {
           |${op.toString.shift(2)}
           |}""".stripMargin
      }
    }

    case class InitProxy(target: RuntimeUniverse.DIKey, dependencies: Set[RuntimeUniverse.DIKey], proxy: MakeProxy) extends ProxyOp {
      override def format: String = f"""$target -> init(${dependencies.mkString(", ")})"""
    }

  }

}



