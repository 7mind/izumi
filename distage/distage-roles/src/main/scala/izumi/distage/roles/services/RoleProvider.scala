package izumi.distage.roles.services

import distage._
import izumi.distage.model.definition.Binding
import izumi.distage.model.definition.Binding.ImplBinding
import izumi.distage.model.reflection.universe.MirrorProvider
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.distage.roles.model.{AbstractRoleF, RoleDescriptor}
import izumi.fundamentals.platform.resources.IzManifest
import izumi.logstage.api.IzLogger

import scala.reflect.ClassTag

trait RoleProvider[F[_]] {
  def getInfo(bindings: Seq[Binding]): RolesInfo
}

object RoleProvider {

  class Impl[F[_] : TagK](
                           logger: IzLogger,
                           requiredRoles: Set[String],
                         ) extends RoleProvider[F] {

    def getInfo(bindings: Seq[Binding]): RolesInfo = {
      val availableBindings = getRoles(bindings)

      val roles = availableBindings.map(_.descriptor.id)

      val enabledRoles = availableBindings
        .filter(b => isEnabledRole(b))

      RolesInfo(
        enabledRoles.map(_.binding.key).toSet,
        enabledRoles,
        roles,
        availableBindings,
        roles.toSet.diff(requiredRoles),
      )
    }

    private def getRoles(bb: Seq[Binding]): Seq[RoleBinding] = {
      bb.flatMap {
        b =>
          b match {
            case s: ImplBinding if isAvailableRoleType(s.implementation.implType) =>
              Seq((b, s.implementation.implType))

            case _ =>
              Seq.empty
          }
      }.flatMap {
        case (rb, impltype) =>
          getDescriptor(rb.key.tpe) match {
            case Some(d) =>
//              val runtimeClass = mirrorProvider.runtimeClass(impltype).getOrElse(classOf[Any])
//              val src = IzManifest.manifest()(ClassTag(runtimeClass)).map(IzManifest.read)
              // FIXME: read runtime class of ROLE INSTANCE, not role descriptor again ???
              val runtimeClass = d.getClass
              val src = IzManifest.manifest()(ClassTag(runtimeClass)).map(IzManifest.read)
              Seq(RoleBinding(rb, runtimeClass, impltype, d, src))
            case None =>
              logger.error(s"${rb.key -> "role"} defined ${rb.origin -> "at"} has no companion object inherited from RoleDescriptor thus ignored")
              Seq.empty
          }
      }
    }

    private def isEnabledRole(b: RoleBinding): Boolean = {
      requiredRoles.contains(b.descriptor.id) || requiredRoles.contains(b.tpe.tag.shortName.toLowerCase)
    }

    private def isAvailableRoleType(tpe: SafeType): Boolean = {
      tpe <:< SafeType.get[AbstractRoleF[F]]
    }

    // FIXME: fix ROleDescriptor instantiation ???
    private def getDescriptor(role: SafeType): Option[RoleDescriptor] = {
      val n = role.tag.shortName
      Some((try {
        Class.forName(s"izumi.distage.roles.test.fixtures.${n}$$").getField("MODULE$").get(null)
      } catch {
        case t: Throwable =>
          println(t.getMessage)
          Class.forName(s"izumi.distage.roles.internal.${n}$$").getField("MODULE$").get(null)
      }).asInstanceOf[RoleDescriptor])
      //      try {
////        role.use(t => mirrorProvider.mirror.reflectModule(t.dealias.companion.typeSymbol.asClass.module.asModule).instance) match {
//        ??? match {
//          case rd: RoleDescriptor => Some(rd)
//          case _ => None
//        }
//      } catch {
//        case t: Throwable =>
//          logger.error(s"Failed to reflect descriptor for $role: $t")
//          None
//      }
    }

  }

}
