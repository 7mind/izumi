package com.github.pshirshov.izumi.sbt

import sbt.Keys._
import sbt._

object ResolverPlugin extends AutoPlugin {

  override lazy val globalSettings = Seq(
    updateOptions := updateOptions
      .value
      .withCachedResolution(true)
      .withGigahorse(true)
  )
}
