package izumi.distage.roles.bundled

import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.effect.DIEffect
import izumi.distage.roles.RoleAppLauncher.Options
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema._
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.strings.IzString._

class Help[F[_]: DIEffect]
(
  roleInfo: RolesInfo,
  activationInfo: ActivationInfo,
) extends RoleTask[F] {

  override def start(@unused roleParameters: RawEntrypointParams, @unused freeArgs: Vector[String]): F[Unit] = {
    DIEffect[F].maybeSuspend(showHelp())
  }

  private[this] def showHelp(): Unit = {
    val descriptors = roleInfo
      .availableRoleBindings
      .map(rb => rb.descriptor.parserSchema)

    val activations = activationInfo.availableChoices
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
      ParserSchema(GlobalArgsSchema(Options, Some(baseDoc), Some(notes)), descriptors)
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
