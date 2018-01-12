package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.model.{DIKey, Formattable}
import org.bitbucket.pshirshov.izumi.di.{CustomDef, TypeFull}

// TODO: typeclass?..
sealed trait ExecutableOp extends Formattable {
  def target: DIKey
}


object ExecutableOp {
  sealed trait FormattableOp extends Formattable {
    this: DependentOp =>

    protected def doFormat(impl: TypeFull, opName: String, opFormat: (Char, Char), delim: (Char, Char)): String = {
      val sb = new StringBuilder()
      sb.append(s"$target := $opName${opFormat._1}${impl.typeSymbol.fullName}${opFormat._2}")
      if (deps.nonEmpty) {
        sb.append(deps.map(_.format).mkString(s" ${delim._1}\n    ", ",\n    ", s"\n${delim._2}"))
      }
      sb.toString()
    }
  }

  sealed trait InstantiationOp extends ExecutableOp

  sealed trait DependentOp extends ExecutableOp {
    def deps: Seq[Association]
  }

  case class InstantiateClass(target: DIKey, impl: TypeFull, deps: Seq[Association]) extends InstantiationOp with DependentOp with FormattableOp {
    override def format: String = doFormat(impl, "make", ('[', ']'), ('(', ')'))
    override def toString: String = format
  }

  case class InstantiateTrait(target: DIKey, impl: TypeFull, deps: Seq[Association]) extends InstantiationOp with DependentOp with FormattableOp {
    override def format: String = doFormat(impl, "impl", ('[', ']'), ('{', '}'))
    override def toString: String = format
  }

  case class InstantiateFactory(target: DIKey, impl: TypeFull, deps: Seq[Association]) extends InstantiationOp with DependentOp with FormattableOp {
    override def format: String = doFormat(impl, "fact", ('[', ']'), ('{', '}'))
    override def toString: String = format
  }

  case class ReferenceInstance(target: DIKey, tpe: TypeFull, instance: Any) extends InstantiationOp {
    override def format: String = {
      s"$target := ${instance.getClass.getCanonicalName}#${instance.hashCode()}"
    }

    override def toString: String = format
  }

  case class ImportDependency(target: DIKey) extends ExecutableOp {
    override def format: String = f"""$target := import $target"""
  }

  trait SetOp extends ExecutableOp {

  }

  case class CreateSet(target: DIKey, tpe: TypeFull) extends SetOp {
    override def format: String = f"""$target := make[$tpe]"""
  }

  case class AddToSet(target: DIKey, element: DIKey) extends SetOp {
    override def format: String = f"""$target += $element"""
  }

  case class CustomOp(target: DIKey, data: CustomDef) extends ExecutableOp {
    override def format: String = f"""$target := custom($target)"""
  }

  case class MakeProxy(op: ExecutableOp, forwardRefs: Set[DIKey]) extends ExecutableOp  {
    override def target: DIKey = op.target
    override def format: String = f"""$target := proxy($op, $forwardRefs)"""
  }

  case class InitProxies(op: ExecutableOp, proxies: Set[DIKey]) extends ExecutableOp {
    override def target: DIKey = op.target
    override def format: String = f"""$target := init($proxies, $op)"""
  }

}



