package com.github.pshirshov.izumi.distage.roles

trait RoleStarter {
  /**
    * Calls [[com.github.pshirshov.izumi.distage.roles.roles.RoleService.start]] on
    * each service in the object graph.
    */
  def start(): Unit

  /**
    * Blocks forever until [[stop]] is called or JVM shutdown happens
    *
    * The application shouldn't touch DI-provided services after this method finishes
    * because the object graph would already be finalized by that moment
    */
  def join(): Unit

  /**
    * This method allows the app to stop waiting on [[join]] and finalize the app
    */
  def stop(): Unit
}
