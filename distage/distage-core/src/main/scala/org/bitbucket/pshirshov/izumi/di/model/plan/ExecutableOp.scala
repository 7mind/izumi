package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.TypeFull
import org.bitbucket.pshirshov.izumi.di.commons.StringUtils
import org.bitbucket.pshirshov.izumi.di.model.plan.Wiring._
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, Formattable}

// TODO: typeclass?..
sealed trait ExecutableOp extends Formattable {
  def target: DIKey

  override def toString: String = format
}


object ExecutableOp {

  sealed trait InstantiationOp extends ExecutableOp

  case class ImportDependency(target: DIKey, references: Set[DIKey]) extends ExecutableOp {
    override def format: String = f"""$target := import $target // required for $references"""
  }

  case class CustomOp(target: DIKey, data: CustomWiring) extends InstantiationOp {
    override def format: String = f"""$target := custom($target)"""
  }

  sealed trait SetOp extends ExecutableOp

  object SetOp {

    case class CreateSet(target: DIKey, tpe: TypeFull) extends SetOp {
      override def format: String = f"""$target := newset[$tpe]"""
    }

    case class AddToSet(target: DIKey, element: DIKey) extends SetOp with InstantiationOp {
      override def format: String = f"""$target += $element"""
    }

  }

  sealed trait WiringOp extends InstantiationOp {
    def wiring: Wiring
  }

  object WiringOp {

    case class InstantiateClass(target: DIKey, wiring: UnaryWiring.Constructor) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    case class InstantiateTrait(target: DIKey, wiring: UnaryWiring.Abstract) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    case class InstantiateFactory(target: DIKey, wiring: Wiring.FactoryMethod) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    case class CallProvider(target: DIKey, wiring: UnaryWiring.Function) extends WiringOp {
      override def format: String = FormattingUtils.doFormat(target, wiring)
    }

    case class ReferenceInstance(target: DIKey, wiring: UnaryWiring.Instance) extends WiringOp {
      override def format: String = {
        s"$target := ${wiring.instance.getClass.getCanonicalName}#${wiring.instance.hashCode()}"
      }
    }

  }

  sealed trait ProxyOp extends ExecutableOp {}

  object ProxyOp {

    case class MakeProxy(op: InstantiationOp, forwardRefs: Set[DIKey]) extends ProxyOp with InstantiationOp {
      override def target: DIKey = op.target

      override def format: String =
        f"""$target := proxy($forwardRefs) {
           |${StringUtils.shift(op.toString, 2)}
           |}""".stripMargin
    }

    case class InitProxy(target: DIKey, dependencies: Set[DIKey], proxy: MakeProxy) extends ProxyOp {
      override def format: String = f"""$target -> init(${dependencies.mkString(", ")})"""
    }

  }

}



