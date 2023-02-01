package izumi.distage.roles.launcher

import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RequiredRoles}

trait AppArgsInterceptor {
  def rolesToLaunch(parsedArgs: RawAppArgs, requiredRoles: RequiredRoles): RawAppArgs
}

object AppArgsInterceptor {
  class Impl extends AppArgsInterceptor {
    def rolesToLaunch(parsedArgs: RawAppArgs, requiredRoles: RequiredRoles): RawAppArgs = {
      val argRoles = parsedArgs.roles
      val argRoleNames = argRoles.map(_.role).toSet
      val nonOverridenRequiredRoles = requiredRoles.requiredRoles.filterNot(argRoleNames contains _.role)
      parsedArgs.copy(roles = argRoles ++ nonOverridenRequiredRoles)
    }
  }
}
