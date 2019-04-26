package com.github.pshirshov.izumi.distage.roles.internal


import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

class Help[F[_] : DIEffect]
(
  roleInfo: RolesInfo,
)
  extends RoleTask[F] {

  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): F[Unit] = {
    Quirks.discard(roleParameters, freeArgs)
    DIEffect[F].maybeSuspend(showHelp())
  }

  private[this] def showHelp(): Unit = {
    val descriptors = roleInfo
      .availableRoleBindings
      .map(rb => rb.descriptor.parserSchema)

    val baseDoc =
      s"""izumi/distage role application launcher
         |
         |  General commanline format:
         |
         |    launcher [launcher options] [:role-name [role options] -- <role-args>]""".stripMargin

    val notes =
      s"""
         |  Notes:
         |
         |    - Config file option (-c) is also appliable to every role individually
         |
         |  Examples:
         |
         |    launcher -c myconfig.json :help :myrole -c roleconfig.json""".stripMargin

    val help = ParserSchemaFormatter.makeDocs(
      ParserSchema(GlobalArgsSchema(RoleAppLauncher.Options, Some(baseDoc), Some(notes)), descriptors)
    )

    println(help)
  }

}

object Help extends RoleDescriptor {
  override final val id = "help"

  override def parserSchema: RoleParserSchema = {
    RoleParserSchema(id, ParserDef.Empty, Some("show commandline help"), None, freeArgsAllowed = false)
  }

}
