package com.github.pshirshov.izumi.sbt.plugins

import sbt.Keys._
import sbt._

object IzumiResolverPlugin extends AutoPlugin {

  override lazy val globalSettings = Seq(
    aggregate in update := false,
    updateOptions := updateOptions
      .value
      .withCachedResolution(true)
      .withGigahorse(true)
      .withLatestSnapshots(true)
      .withInterProjectFirst(true),
  )
}
