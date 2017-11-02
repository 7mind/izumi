package org.bitbucket.pshirshov.izumi.sbt.definitions

class GlobalDefs(override protected val globalSettings: GlobalSettings) extends IzumiDsl {
  protected def init(): Unit = {
    addExtender(new GlobalSettingsExtender(globalSettings))
    addExtender(new SharedDepsExtender(globalSettings))
    addExtender(new GlobalSettingsExtender(globalSettings))
    super.setup()
  }

  init()
}
