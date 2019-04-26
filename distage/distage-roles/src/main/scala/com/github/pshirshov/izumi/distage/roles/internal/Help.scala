package com.github.pshirshov.izumi.distage.roles.internal


import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import com.github.pshirshov.izumi.fundamentals.platform.cli.ParserFailureHandler
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema.{RoleParserSchema, ParserSchema, ParserSchemaFormatter}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

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
      .map(rb => RoleParserSchema(rb.descriptor.id, rb.descriptor.parser, rb.descriptor.doc))

    val roleHelp = ParserSchemaFormatter.makeDocs(ParserSchema(descriptors))

    val mainHelp = ParserSchemaFormatter.formatOptions(RoleAppLauncher.Options).map(_.shift(2))

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
