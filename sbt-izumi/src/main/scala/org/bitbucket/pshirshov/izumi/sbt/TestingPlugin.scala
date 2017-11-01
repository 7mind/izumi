package org.bitbucket.pshirshov.izumi.sbt

import sbt._

object TestingPlugin extends AutoPlugin {

  override lazy val globalSettings = Seq(
    Keys.testOptions in Test ++= Seq(Tests.Argument("-oD"), Tests.Argument("-eD"))
  )

  override def projectSettings = Seq(
  )
}
