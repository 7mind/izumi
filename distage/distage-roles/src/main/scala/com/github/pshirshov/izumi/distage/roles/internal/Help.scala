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
      .mkString("\n\n")

    val mainHelp = ParserDef.formatOptions(RoleAppLauncher.Options).map(_.shift(2))

    val fullHelp =
      s"""${ParserFailureHandler.example}
         |
         |Global options:
         |
         |${mainHelp.getOrElse("?")}
         |
         |Available roles:
         |
         |${roleHelp.shift(2)}
       """.stripMargin

    println(fullHelp)
  }

  private[this] def formatRoleHelp(rb: RoleBinding): String = {
    val sub = Seq(rb.descriptor.doc.toSeq, ParserDef.formatOptions(rb.descriptor.parser).toSeq).flatten.mkString("\n\n")

    val id = s":${rb.descriptor.id}"

    if (sub.nonEmpty) {
      Seq(id, sub.shift(2)).mkString("\n\n")
    } else {
      id
    }
  }

}

object Help extends RoleDescriptor {
  override final val id = "help"

  override def doc: Option[String] = Some("show commandline help")


}
