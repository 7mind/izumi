package com.github.pshirshov.izumi.distage.roles.internal

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import com.github.pshirshov.izumi.fundamentals.platform.cli.ParserDef.ParserRoleDescriptor
import com.github.pshirshov.izumi.fundamentals.platform.cli.{Parameters, ParserDef, ParserFailureHandler}
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
    val descriptors = roleInfo
      .availableRoleBindings
      .map(rb => ParserRoleDescriptor(rb.descriptor.id, rb.descriptor.parser, rb.descriptor.doc))

    val roleHelp = ParserDef.formatRoles(descriptors)

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

}

object Help extends RoleDescriptor {
  override final val id = "help"

  override def doc: Option[String] = Some("show commandline help")


}
