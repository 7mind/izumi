package org.bitbucket.pshirshov.izumi.sbt

import sbt.AutoPlugin

object IzumiImportsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  //noinspection TypeAnnotation
  object autoImport {
    val IzumiDsl = definitions.IzumiDsl
    val IzumiScopes = definitions.IzumiScopes

    val GitStampPlugin = org.bitbucket.pshirshov.izumi.sbt.GitStampPlugin

    type GlobalDefs = definitions.GlobalDefs
    type GlobalSettings = definitions.GlobalSettings
    type TwinRepo = org.bitbucket.pshirshov.izumi.sbt.TwinRepo
  }
}
