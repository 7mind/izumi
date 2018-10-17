package com.github.pshirshov.izumi.distage.roles.roles

trait IntegrationComponent {
  /**This method must never throw
    */
  def resourcesAvailable(): ResourceCheck
}
