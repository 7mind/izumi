package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.models.FileRotation

object Test extends App {
      val a = new FileSink(2, new DummyFileService(), FileRotation.FileLimiterRotation(3), "logstage")
      a
      (1 to 29).map(i => s"sss$i").foreach(a.sendMessage)
      println(a.sinkState.get())
}
