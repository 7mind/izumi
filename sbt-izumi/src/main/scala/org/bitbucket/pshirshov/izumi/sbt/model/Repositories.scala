package com.github.pshirshov.izumi.sbt.model

import sbt.librarymanagement.Resolver


trait Repositories {
  def get(filter: Repo => Boolean): Seq[Resolver]

  def get(snapshots: Boolean): Seq[Resolver] = {
    get(r => !r.isSnaphot || r.isSnaphot == snapshots)
  }
}


