package com.github.pshirshov.izumi.sbt.model

import sbt.librarymanagement.Resolver

case class Repo(resolver: Resolver, isSnaphot: Boolean)
