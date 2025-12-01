package izumi.distage.roles.launcher

import izumi.fundamentals.platform.cli.model.{RequiredRoles, RoleAppArgs}

trait AppArgsInterceptor {
  def rolesToLaunch(parsedArgs: RoleAppArgs, requiredRoles: RequiredRoles): RoleAppArgs
}

object AppArgsInterceptor {
  class Impl extends AppArgsInterceptor {
    def rolesToLaunch(parsedArgs: RoleAppArgs, requiredRoles: RequiredRoles): RoleAppArgs = {
      val argRoles = parsedArgs.roles
      val argRoleNames = argRoles.map(_.role).toSet
      val nonOverridenRequiredRoles = requiredRoles.requiredRoles.filterNot(argRoleNames contains _.role)
      parsedArgs.copy(roles = argRoles ++ nonOverridenRequiredRoles)
    }
  }
}
