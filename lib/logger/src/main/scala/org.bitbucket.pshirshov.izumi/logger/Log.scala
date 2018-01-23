package org.bitbucket.pshirshov.izumi.logger

object Log {
  sealed trait Level {
  } // enum

  object Level {
    case object Debug extends Level
    case object Info extends Level
    case object Warn extends Level
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

  case class Message(template: StringContext, args: Any*)

  case class Entry(message: Message, context: Context)

  case class LogMapping(filter: LogFilter, sink: LogSink)
}

object Models {

  case class LoggingConfig(rules: Seq[LoggingRule] = Seq.empty, default: Set[Log.Level] = Set(Log.Level.Debug))

  case class LoggingRule(packageName: String = "", levels: Set[Log.Level])

}
