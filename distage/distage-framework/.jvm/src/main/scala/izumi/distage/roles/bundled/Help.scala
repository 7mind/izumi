package izumi.distage.roles.bundled

import izumi.distage.framework.model.ActivationInfo
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.cli.model.schema.*
import izumi.fundamentals.platform.strings.IzString.*

import scala.annotation.unused

class Help[F[_]](
  roleInfo: RolesInfo,
  activationInfo: ActivationInfo,
  F: QuasiIO[F],
) extends RoleTask[F]
  with BundledTask {

  override def start(@unused roleParameters: RawEntrypointParams, @unused freeArgs: Vector[String]): F[Unit] = {
    F.maybeSuspend(showHelp())
  }

  private def showHelp(): Unit = {
    val descriptors = roleInfo.availableRoleBindings
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
      ParserSchema(GlobalArgsSchema(RoleAppMain.Options, Some(baseDoc), Some(notes)), descriptors.toIndexedSeq.sortBy(_.id))
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
