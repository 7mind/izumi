package com.github.pshirshov.izumi.sbt.model

import sbt.librarymanagement.Resolver


case class TwinRepo(name: String, base: String, releases: String, snapshots: String) extends Repositories {
  override def get(filter: Repo => Boolean): Seq[Resolver] = {
    import sbt.librarymanagement.syntax._

    Seq(
      Repo(s"$name-releases" at s"$base/$releases", isSnaphot = false)
      , Repo(s"$name-snapshots" at s"$base/$snapshots", isSnaphot = true)
    )
      .filter(filter)
      .map(_.resolver)
  }
}
