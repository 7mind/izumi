package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.Symb
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, Formattable}

sealed trait ExecutableOp extends Formattable{
  def target: DIKey
}


object ExecutableOp {

  sealed trait InstantiationOp extends ExecutableOp

  case class InstantiateClass(target: DIKey, impl: Symb, args: Seq[Association]) extends InstantiationOp {
    override def format: String = {
      val sb = new StringBuilder()
      sb.append(f"$target := make[${impl.fullName}]")
      if (args.nonEmpty) {
        sb.append(args.map(_.format).mkString(" (\n    ", ",\n    ", "\n)"))
      }
      sb.toString()
    }

    override def toString: String = format
  }

  case class InstantiateTrait(target: DIKey, impl: Symb, args: Seq[Association]) extends InstantiationOp {
    override def format: String = {
      val sb = new StringBuilder()
      sb.append(f"$target := impl[${impl.fullName}]")
      if (args.nonEmpty) {
        sb.append(args.map(_.format).mkString(" {\n    ", ",\n    ", "\n}"))
      }
      sb.toString()
    }

    override def toString: String = format
  }

  case class InstantiateFactory(target: DIKey, impl: Symb, materials: Seq[Association]) extends InstantiationOp {
    override def format: String = {
      val sb = new StringBuilder()
      sb.append(f"$target := mkft[${impl.fullName}]")
      if (materials.nonEmpty) {
        sb.append(materials.map(_.format).mkString(" {\n    ", ",\n    ", "\n}"))
      }
      sb.toString()
    }

    override def toString: String = format
  }

  case class ReferenceInstance(target: DIKey, instance: AnyRef) extends InstantiationOp {
    override def format: String = {
      s"$target := ${instance.getClass.getCanonicalName}#${instance.hashCode()}"
    }

    override def toString: String = format
  }

  case class ImportDependency(target: DIKey) extends ExecutableOp {
    override def format: String = f"""$target := import $target"""
  }

  case class MakeProxy(target: DIKey, op: InstantiationOp) extends ExecutableOp {
    override def format: String = ???
  }

  case class InitProxy(target: DIKey) extends ExecutableOp {
    override def format: String = ???
  }

}



