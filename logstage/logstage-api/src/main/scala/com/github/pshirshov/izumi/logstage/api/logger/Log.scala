package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.model.Message


object Log {
  sealed trait Level {
  } // enum

  object Level {
    case object Debug extends Level
    case object Info extends Level
    case object Warn extends Level
    case object Error extends Level
  }


  trait CustomContext {
    def values: Map[String, Any]
  }

  case object EmptyCustomContext extends CustomContext {
    override def values: Map[String, Any] = Map.empty
  }


  case class StaticContext(id: String)
  case class ThreadData(threadName: String, threadId: Long)
  case class DynamicContext(level: Level, threadData: ThreadData)
  case class Context(static: StaticContext, dynamic: DynamicContext, customContext: CustomContext)


  case class NamedArgument(name : String, value : Any)

  case class Entry(message: Message, context: Context)

}


