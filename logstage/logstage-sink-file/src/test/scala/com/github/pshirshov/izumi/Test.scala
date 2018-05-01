package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.models.FileRotation

object Test extends App {
      val config = FileSinkConfig(2, "logstage")
      val sink = new FileSink(new DummyFileService(), FileRotation.FileLimiterRotation(3), config)
      (1 to 29).map(i => s"sss$i").foreach(sink.sendMessage)
      println(sink.sinkState.get())
}
