package com.github.pshirshov.izumi.sbt.plugins

import sbt.Keys._
import sbt._

object IzumiResolverPlugin extends AutoPlugin {

  override lazy val globalSettings = Seq(
    aggregate in update := false,
    updateOptions := updateOptions
      .value
      .withInterProjectFirst(true)
      .withCachedResolution(true)
      .withGigahorse(true)
      .withLatestSnapshots(false)
    ,
  )
}
