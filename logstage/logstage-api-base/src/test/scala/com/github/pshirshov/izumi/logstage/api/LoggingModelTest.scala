package com.github.pshirshov.izumi.logstage.api

import org.scalatest.WordSpec


class LoggingModelTest extends WordSpec {

  "Log levels" should {
    "be comparable" in {
      assert(Log.Level.Debug < Log.Level.Error)
      assert(Log.Level.Debug <= Log.Level.Debug)
    }
  }

}
