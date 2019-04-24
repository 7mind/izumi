package com.github.pshirshov.izumi.distage.roles.internal

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher
import com.github.pshirshov.izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import com.github.pshirshov.izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import com.github.pshirshov.izumi.fundamentals.platform.cli.{CLIParser, Parameters, ParserDef, ParserFailureHandler}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._


class Help[F[_] : DIEffect]
(
  roleInfo: RolesInfo,
)
  extends RoleTask[F] {

  override def start(roleParameters: Parameters, freeArgs: Vector[String]): F[Unit] = {
    Quirks.discard(roleParameters, freeArgs)
    DIEffect[F].maybeSuspend(showHelp())
  }

  private[this] def showHelp(): Unit = {
    val roleHelp = roleInfo
      .availableRoleBindings
      .map(formatRoleHelp)
      .mkString("\n")

    val mainHelp = formatOptions(RoleAppLauncher.Options)

    val fullHelp =
      s"""${ParserFailureHandler.example}
         |
         |Global options:
         |
         |${mainHelp.shift(2)}
         |
         |Available roles:
         |
         |${roleHelp.shift(2)}
       """.stripMargin

    println(fullHelp)
  }

  private[this] def formatRoleHelp(rb: RoleBinding): String = {
    val sub = formatOptions(rb.descriptor.parser)
    s":${rb.descriptor.id}\n" + (if (sub.nonEmpty) {
      "\n" + sub.shift(2)
    } else {
      ""
    })
  }

  private[this] def formatOptions(parser: ParserDef): String = {
    parser.all.values.map(formatArg).mkString("\n\n")
  }

  private[this] def formatArg(arg: CLIParser.ArgDef): String = {
    arg.name.short match {
      case Some(value) =>
        s"-$value, ${formatInfo(arg)}"
      case None =>
        formatInfo(arg)
    }
  }

  private[this] def formatInfo(arg: CLIParser.ArgDef): String = {
    s"--${arg.name.long}\n    ${arg.doc}"
  }
}

object Help extends RoleDescriptor {
  override final val id = "help"

  override def parser: ParserDef = ParserDef.Empty
}
