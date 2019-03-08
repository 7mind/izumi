package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, Wiring}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

trait OpFormatter {
  def format(op: ExecutableOp): String
}

object OpFormatter {
  def formatBindingPosition(origin: Option[Binding]): String = {
    origin.fold("(<unknown>)")(_.origin.toString)
  }

  class Impl(kf: KeyFormatter, tf: TypeFormatter) extends OpFormatter {
    override def format(op: ExecutableOp): String = {
      import ExecutableOp._
      import ProxyOp._
      import WiringOp._
      op match {
        case i: InstantiationOp =>
          i match {
            case CreateSet(target, tpe, members, origin) =>
              // f"""$target := newset[$tpe]"""
              val repr = doFormat(tf.format(tpe), members.map(kf.format).toSeq, "newset", ('[', ']'), ('{', '}'))
              val pos = formatBindingPosition(origin)
              s"${kf.format(target)} $pos := $repr"

            case w: WiringOp =>
              w match {
                case InstantiateClass(target, wiring, origin) =>
                  doFormat(target, wiring, origin)
                case InstantiateTrait(target, wiring, origin) =>
                  doFormat(target, wiring, origin)
                case InstantiateFactory(target, wiring, origin) =>
                  doFormat(target, wiring, origin)
                case CallProvider(target, wiring, origin) =>
                  doFormat(target, wiring, origin)
                case CallFactoryProvider(target, wiring, origin) =>
                  doFormat(target, wiring, origin)
                case ReferenceInstance(target, wiring, origin) =>
                  val pos = formatBindingPosition(origin)
                  if (wiring.instance!=null) {
                    s"${kf.format(target)} $pos := ${wiring.instance.getClass}#${wiring.instance.hashCode()}"
                  } else {
                    s"${kf.format(target)} $pos := null"
                  }

                case ReferenceKey(target, wiring, origin) =>
                  val pos = formatBindingPosition(origin)
                  s"${kf.format(target)} $pos := ${kf.format(wiring.key)}"
              }
          }

        case ImportDependency(target, references, origin) =>
          val pos = formatBindingPosition(origin)
          s"${kf.format(target)} $pos := import $target // required for ${references.map(kf.format).mkString(" and ")}"

        case p: ProxyOp =>
          p match {
            case MakeProxy(proxied, forwardRefs, origin, byNameAllowed) =>

              val pos = formatBindingPosition(origin)
              val kind = if (byNameAllowed) {
                "proxy.light"
              } else {
                "proxy.cogen"
              }

              s"""${kf.format(p.target)} $pos := $kind(${forwardRefs.map(s => s"${kf.format(s)}: deferred").mkString(", ")}) {
                 |${format(proxied).shift(2)}
                 |}""".stripMargin

            case ProxyOp.InitProxy(target, dependencies, proxy@_, origin) =>
              val pos = formatBindingPosition(origin)
              s"${kf.format(target)} $pos -> init(${dependencies.map(kf.format).mkString(", ")})"

          }
      }
    }

    private def doFormat(target: DIKey, deps: Wiring, origin: Option[Binding]): String = {
      val op = doFormat(deps)
      val pos = formatBindingPosition(origin)
      s"${kf.format(target)} $pos := $op"
    }


    private def doFormat(deps: Wiring): String = {
      deps match {
        case Constructor(instanceType, associations, prefix) =>
          doFormat(tf.format(instanceType.tpe), formatPrefix(prefix) ++ associations.map(doFormat), "make", ('[', ']'), ('(', ')'))

        case AbstractSymbol(instanceType, associations, prefix) =>
          doFormat(tf.format(instanceType.tpe), formatPrefix(prefix) ++ associations.map(doFormat), "impl", ('[', ']'), ('{', '}'))

        case Function(instanceType, associations) =>
          doFormat(doFormat(instanceType), associations.map(doFormat), "call", ('(', ')'), ('{', '}'))

        case Factory(factoryType, factoryIndex, dependencies) =>
          val wirings = factoryIndex.map {
            w =>
              s"${w.factoryMethod}: ${tf.format(w.factoryMethod.finalResultType)} ~= ${doFormat(w.wireWith)}".shift(2)
          }

          val depsRepr = dependencies.map(doFormat)

          doFormat(
            tf.format(factoryType)
            , wirings ++ depsRepr
            , "factory", ('(', ')'), ('{', '}')
          )

        case FactoryFunction(factoryType, factoryIndex, dependencies) =>
          val wirings = factoryIndex.map {
            case (idx, w) =>
              s"${w.factoryMethod}[$idx]: ${tf.format(w.factoryMethod.finalResultType)} ~= ${doFormat(w.wireWith)}".shift(2)
          }.toSeq

          val depsRepr = dependencies.map(doFormat)

          doFormat(
            doFormat(factoryType)
            , wirings ++ depsRepr
            , "call factory", ('(', ')'), ('{', '}')
          )

        case other =>
          s"UNEXPECTED WIREABLE: $other"
      }
    }

    private def doFormat(association: RuntimeDIUniverse.Association): String = {
      association match {
        case RuntimeDIUniverse.Association.Parameter(_, name, tpe, wireWith, isByName, _) =>
          val fname = if (isByName) {
            s"=> $name"
          } else {
            name
          }

          s"""par $fname: ${tf.format(tpe)} = lookup(${kf.format(wireWith)})"""

        case RuntimeDIUniverse.Association.AbstractMethod(_, name, tpe, wireWith) =>
          s"""def $name: ${tf.format(tpe)} = lookup(${kf.format(wireWith)})"""
      }
    }

    private def doFormat(provider: RuntimeDIUniverse.Provider) = {
      s"${provider.fun}(${provider.argTypes.map(tf.format).mkString(", ")}): ${tf.format(provider.ret)}"
    }

    private def formatPrefix(prefix: Option[RuntimeDIUniverse.DIKey]): Seq[String] = {
      prefix.toSeq.map(p => s".prefix = lookup(${kf.format(p)})")
    }

    private def doFormat(impl: String, depRepr: Seq[String], opName: String, opFormat: (Char, Char), delim: (Char, Char)): String = {
      val sb = new StringBuilder()
      sb.append(s"$opName${opFormat._1}$impl${opFormat._2} ${delim._1}")
      if (depRepr.nonEmpty) {
        sb.append("\n")
        sb.append(depRepr.mkString("\n").shift(2))
        sb.append(s"\n${delim._2}")
      } else {
        sb.append(delim._2)
      }

      sb.toString()
    }
  }

}
