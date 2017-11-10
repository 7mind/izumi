package org.bitbucket.pshirshov.izumi.sbt.definitions

class GlobalDefs(override protected val globalSettings: GlobalSettings) extends IzumiDsl {
  protected def init(): Unit = {
    val projectSettings = globalSettings.globalSettingsGroup
    addExtender(new GlobalSettingsExtender(projectSettings))
    addExtender(new SharedDepsExtender(projectSettings))
    addExtender(new GlobalSettingsExtender(projectSettings))
    super.setup()
  }

  init()
}
