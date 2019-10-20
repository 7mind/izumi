package izumi.distage.roles.internal

import izumi.distage.model.monadic.DIEffect
import izumi.distage.roles.RoleAppLauncher
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.model.{AppActivation, RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema._
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.strings.IzString._

class Help[F[_] : DIEffect]
(
  roleInfo: RolesInfo,
  activation: AppActivation,
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

    val activations = activation.choices
      .map {
        case (axis, members) =>
          s"$axis:${members.niceList().shift(2)}"
      }
      .niceList().shift(2)

    val baseDoc =
      s"""izumi/distage role application launcher
         |
         |  General commandline format:
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
         |    launcher -c myconfig.json :help :myrole -c roleconfig.json
         |
         |Available functionality choices:
         |$activations""".stripMargin

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
