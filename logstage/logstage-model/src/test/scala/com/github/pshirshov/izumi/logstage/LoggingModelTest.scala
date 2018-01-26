package com.github.pshirshov.izumi.logstage

import com.github.pshirshov.izumi.logstage.model.Log
import org.scalatest.WordSpec

class LoggingModelTest extends WordSpec {

  "Log levels" should {
    "be comparable" in {
      assert(Log.Level.Debug < Log.Level.Error)
      assert(Log.Level.Debug <= Log.Level.Debug)
    }
  }

}
