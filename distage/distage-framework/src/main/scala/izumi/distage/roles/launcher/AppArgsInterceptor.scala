package izumi.distage.roles.launcher

import izumi.distage.roles.RoleAppMain.AdditionalRoles
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs

trait AppArgsInterceptor {
  def rolesToLaunch(parsedArgs: RawAppArgs, additionalRoles: AdditionalRoles): RawAppArgs
}

object AppArgsInterceptor {
  class AppArgsInterceptorImpl() extends AppArgsInterceptor {
    def rolesToLaunch(parsedArgs: RawAppArgs, additionalRoles: AdditionalRoles): RawAppArgs = {
      val requestedRoleSet = parsedArgs.roles.map(_.role).toSet
      parsedArgs.copy(roles = parsedArgs.roles ++ additionalRoles.knownRequiredRoles.filterNot(requestedRoleSet contains _.role))

    }
  }
}
