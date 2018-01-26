package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.model.Log.{StaticContext, ThreadData}


trait WithLogContext {

  implicit val context: StaticContext = StaticContext(this.getClass.getCanonicalName)

  private val threadData = new ThreadLocal[ThreadData]() {
    override def initialValue(): ThreadData = {
      val thread = Thread.currentThread()
      ThreadData(thread.getName, thread.getId)
    }
  }

  implicit def thread: ThreadData = threadData.get()
}



