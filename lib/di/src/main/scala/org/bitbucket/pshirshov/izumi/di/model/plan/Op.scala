package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.Symb
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, Formattable}

sealed trait Op extends Formattable{
  def target: DIKey
}


object Op {

  sealed trait InstantiationOp extends Op

  case class InstantiateClass(target: DIKey, impl: Symb, args: Seq[Association]) extends InstantiationOp {
    override def format: String = {
      val sb = new StringBuilder()
      sb.append(f"$target := new ${target.symbol.fullName}")
      if (args.nonEmpty) {
        sb.append(args.map(_.format).mkString(" (\n    ", ",\n    ", "\n  )"))
      }
      sb.toString()
    }
  }

  case class InstantiateTrait(target: DIKey, impl: Symb, args: Seq[Association]) extends InstantiationOp {
    override def format: String = {
      val sb = new StringBuilder()
      sb.append(f"$target := impl ${target.symbol.fullName}")
      if (args.nonEmpty) {
        sb.append(args.map(_.format).mkString(" {\n    ", ",\n    ", "\n  }"))
      }
      sb.toString()
    }
  }

  case class PullDependency(target: DIKey) extends Op {
    override def format: String = f"""$target := fetch $target"""
  }

  case class MakeProxy(target: DIKey, op: InstantiationOp) extends Op {
    override def format: String = ???
  }

  case class InitProxy(target: DIKey) extends Op {
    override def format: String = ???
  }

}