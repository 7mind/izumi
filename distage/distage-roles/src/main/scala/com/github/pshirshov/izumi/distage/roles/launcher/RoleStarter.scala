package com.github.pshirshov.izumi.distage.roles.launcher

trait RoleStarter {
  /**
    * Calls [[com.github.pshirshov.izumi.distage.roles.roles.RoleService.start()]] on
    * each service in context.
    */
  def start(): Unit

  /**
    * Blocks forever until [[stop()]] is called or JVM shutdown happens
    *
    * The application shouldn't touch DI-provided services after this method finishes
    * because context would be finalized at that moment
    */
  def join(): Unit

  /**
    * This method allows the app to stop waiting on [[join()]] and finalize the app
    */
  def stop(): Unit
}
