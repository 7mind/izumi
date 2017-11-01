package org.bitbucket.pshirshov.izumi.sbt

import sbt._

object BuildPlugin extends AutoPlugin {

  override lazy val globalSettings = Seq(
      //compileOrder := CompileOrder.Mixed
  )

}

