package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.{CustomDef, TypeFull}
import org.bitbucket.pshirshov.izumi.di.model.plan.Wireable._
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, Formattable}

// TODO: typeclass?..
sealed trait ExecutableOp extends Formattable {
  def target: DIKey

  override def toString: String = format
}

object ExecutableOp {

  sealed trait FormattableOp extends Formattable {
    //this: WiringOp =>

    protected def doFormat(target: DIKey, deps: Wireable): String = {
      val op = doFormat(deps, 0)
      s"$target := $op"

    }

    private def doFormat(deps: Wireable, shift: Int): String = {
      deps match {
        case Constructor(instanceType, _, associations) =>
          doFormat(instanceType.tpe.toString, associations.map(_.format), "make", ('[', ']'), ('(', ')'), shift)
        case Abstract(instanceType, associations) =>
          doFormat(instanceType.tpe.toString, associations.map(_.format), "impl", ('[', ']'), ('{', '}'), shift)

        case Function(instanceType, associations) =>
          doFormat(instanceType.toString, associations.map(_.format), "call", ('(', ')'), ('{', '}'), shift)

        case Indirect(toWire, wireWith) =>
          s"$toWire ~= ${doFormat(wireWith, shift)}"

        case FactoryMethod(factoryType, unaryWireables) =>
          doFormat(
            factoryType.toString
            , unaryWireables.map(w => doFormat(w, shift + 1))
            , "fact", ('(', ')'), ('{', '}')
            , shift
          )

        case other =>
          s"UNEXPECTED WIREABLE: $other"
      }
    }

    private def doFormat(impl: String, depRepr: Seq[String], opName: String, opFormat: (Char, Char), delim: (Char, Char), shift: Int): String = {
      val sb = new StringBuilder()
      val curShift = "  " * shift
      val subshift = "  " * (shift + 1)
      sb.append(s"$opName${opFormat._1}$impl${opFormat._2}")
      if (depRepr.nonEmpty) {
        sb.append(depRepr.mkString(s" ${delim._1}\n$subshift", s",\n$subshift", s"\n$curShift${delim._2}"))
      }
      sb.toString()
    }
  }

  sealed trait InstantiationOp extends ExecutableOp

  case class ImportDependency(target: DIKey, references: Set[DIKey]) extends ExecutableOp {
    override def format: String = f"""$target := import $target // required for $references"""
  }

  case class ReferenceInstance(target: DIKey, tpe: TypeFull, instance: Any) extends InstantiationOp {
    override def format: String = {
      s"$target := ${instance.getClass.getCanonicalName}#${instance.hashCode()}"
    }
  }

  case class CustomOp(target: DIKey, data: CustomDef) extends InstantiationOp {
    override def format: String = f"""$target := custom($target)"""
  }

  sealed trait SetOp extends ExecutableOp

  object SetOp {

    case class CreateSet(target: DIKey, tpe: TypeFull) extends ExecutableOp with SetOp {
      override def format: String = f"""$target := newset[$tpe]"""
    }

    case class AddToSet(target: DIKey, element: DIKey) extends InstantiationOp with SetOp {
      override def format: String = f"""$target += $element"""
    }

  }

  sealed trait WiringOp extends InstantiationOp {
    def deps: Wireable
  }

  object WiringOp {

    case class InstantiateClass(target: DIKey, deps: Wireable.Constructor) extends InstantiationOp with FormattableOp {
      override def format: String = doFormat(target, deps)
    }

    case class InstantiateTrait(target: DIKey, deps: Wireable.Abstract) extends InstantiationOp with FormattableOp {
      override def format: String = doFormat(target, deps)
    }

    case class InstantiateFactory(target: DIKey, deps: Wireable.FactoryMethod) extends InstantiationOp with FormattableOp {
      override def format: String = doFormat(target, deps)
    }

    case class CallProvider(target: DIKey, tpe: TypeFull, deps: Wireable.Function) extends InstantiationOp with FormattableOp {
      override def format: String = doFormat(target, deps)
    }

  }

  sealed trait ProxyOp {}

  object ProxyOp {
    case class MakeProxy(op: ExecutableOp, forwardRefs: Set[DIKey]) extends InstantiationOp {
      override def target: DIKey = op.target

      override def format: String = f"""$target := proxy($op, $forwardRefs)"""
    }

    case class InitProxies(op: ExecutableOp, proxies: Set[DIKey]) extends InstantiationOp {
      override def target: DIKey = op.target

      override def format: String = f"""$target := init($proxies, $op)"""
    }

  }
}



