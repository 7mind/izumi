package org.bitbucket.pshirshov.izumi.sbt

import sbt.Keys._
import sbt._

object ResolverPlugin extends AutoPlugin {

  override lazy val globalSettings = Seq(
    updateOptions := updateOptions
      .value
      .withCachedResolution(true)
      //.withGigahorse(true)
    , pomIncludeRepository := (_ => false)
  )

  override def projectSettings = Seq(
    //aggregate in update := false
  )
}
