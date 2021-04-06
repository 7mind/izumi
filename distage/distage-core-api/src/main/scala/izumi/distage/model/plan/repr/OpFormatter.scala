package izumi.distage.model.plan.repr

import izumi.distage.model.plan.ExecutableOp.MonadicOp._
import izumi.distage.model.plan.ExecutableOp.ProxyOp._
import izumi.distage.model.plan.ExecutableOp.WiringOp._
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, InstantiationOp, WiringOp, _}
import izumi.distage.model.plan.Wiring.MonadicWiring._
import izumi.distage.model.plan.Wiring.SingletonWiring._
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.plan.{ExecutableOp, Wiring}
import izumi.distage.model.reflection.{DIKey, LinkedParameter, Provider}
import izumi.fundamentals.platform.strings.IzString._

trait OpFormatter {
  def format(op: ExecutableOp): String
}

object OpFormatter {
  def formatBindingPosition(origin: EqualizedOperationOrigin): String = {
    origin.value match {
      case OperationOrigin.UserBinding(binding) =>
        binding.origin.toString
      case OperationOrigin.SyntheticBinding(binding) =>
        binding.origin.toString
      case OperationOrigin.Unknown =>
        "(<unknown>)"
    }
  }

  def apply(keyFormatter: KeyFormatter, typeFormatter: TypeFormatter): OpFormatter = new OpFormatter.Impl(keyFormatter, typeFormatter)

  class Impl(
    keyFormatter: KeyFormatter,
    typeFormatter: TypeFormatter,
  ) extends OpFormatter {

    import keyFormatter.formatKey
    import typeFormatter.formatType

    override def format(op: ExecutableOp): String = {
      op match {
        case i: InstantiationOp =>
          i match {
            case CreateSet(target, members, origin) =>
              val repr = doFormat("set", members.map(formatKey).toSeq, "newset", ('[', ']'), ('{', '}'))
              val pos = formatBindingPosition(origin)
              s"${formatKey(target)} $pos := $repr"

            case ExecuteEffect(target, effectKey, _, effectHKTypeCtor, origin) =>
              val pos = formatBindingPosition(origin)
              s"${formatKey(target)} $pos := effect[$effectHKTypeCtor]${formatKey(effectKey)}"

            case AllocateResource(target, effectKey, _, effectHKTypeCtor, origin) =>
              val pos = formatBindingPosition(origin)
              s"${formatKey(target)} $pos := allocate[$effectHKTypeCtor]${formatKey(effectKey)}"

            case w: WiringOp =>
              w match {
                case CallProvider(target, wiring, origin) =>
                  formatProviderOp(target, wiring, origin)
                case UseInstance(target, wiring, origin) =>
                  val pos = formatBindingPosition(origin)
                  if (wiring.instance != null) {
                    s"${formatKey(target)} $pos := value ${wiring.instance.getClass.getName}#${wiring.instance.hashCode()}"
                  } else {
                    s"${formatKey(target)} $pos := null"
                  }
                case ReferenceKey(target, wiring, origin) =>
                  val pos = formatBindingPosition(origin)
                  s"${formatKey(target)} $pos := ref ${formatKey(wiring.key)}"
              }
          }

        case ImportDependency(target, references, origin) =>
          val pos = formatBindingPosition(origin)
          val hint = if (references.nonEmpty) {
            s"// required for ${references.map(formatKey).mkString(" and ")}"
          } else {
            " // no dependees"
          }
          s"${formatKey(target)} $pos := import $target $hint".trim

        case p: ProxyOp =>
          p match {
            case MakeProxy(proxied, forwardRefs, origin, byNameAllowed) =>
              val pos = formatBindingPosition(origin)
              val kind = if (byNameAllowed) {
                "proxy.light"
              } else {
                "proxy.cogen"
              }

              s"""${formatKey(p.target)} $pos := $kind(${forwardRefs.map(s => s"${formatKey(s)}: deferred").mkString(", ")}) {
                 |${format(proxied).shift(2)}
                 |}""".stripMargin

            case ProxyOp.InitProxy(target, dependencies, proxy, origin) =>
              val pos = formatBindingPosition(origin)
              s"${formatKey(target)} $pos -> init[${proxy.target}](${dependencies.map(formatKey).mkString(", ")})"

          }
      }
    }

    private def formatProviderOp(target: DIKey, deps: Wiring, origin: EqualizedOperationOrigin): String = {
      val op = formatProviderWiring(deps)
      val pos = formatBindingPosition(origin)
      s"${formatKey(target)} $pos := $op"
    }

    private def formatProviderWiring(deps: Wiring): String = {
      deps match {
        case f: Function =>
          doFormat(formatFunction(f.provider), f.associations.map(formatDependency), "call", ('(', ')'), ('{', '}'))

        case other @ (_: Effect | _: Resource | _: Instance | _: Reference) =>
          s"UNEXPECTED WIREABLE: $other"
      }
    }

    private def formatDependency(association: LinkedParameter): String = {
      association match {
        case p: LinkedParameter =>
          val fname = if (p.isByName) {
            s"=> ${p.name}"
          } else {
            p.name
          }

          s"""arg $fname: ${formatType(p.symbol.finalResultType)} = lookup(${formatKey(p.key)})"""
      }
    }

    private def formatFunction(provider: Provider): String = {
      s"${provider.funString}(${provider.argTypes.map(formatType).mkString(", ")}): ${formatType(provider.ret)}"
    }

    //    private def formatPrefix(prefix: Option[DIKey]): Seq[String] = {
    //      prefix.toSeq.map(p => s".prefix = lookup(${formatKey(p)})")
    //    }

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
