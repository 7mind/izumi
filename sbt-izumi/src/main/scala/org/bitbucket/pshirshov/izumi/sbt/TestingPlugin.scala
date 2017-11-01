package org.bitbucket.pshirshov.izumi.sbt

import sbt._

object TestingPlugin extends AutoPlugin {

  override lazy val globalSettings = Seq(
    Keys.testOptions in Test += Tests.Argument("-oD -eD")
  )

  override def projectSettings = Seq(
  )
}
