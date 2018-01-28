package com.github.pshirshov.izumi.logstage.model

object Log {

  sealed trait Level extends Ordered[Level] {
    protected def asInt: Int

    override def compare(that: Level): Int = this.asInt - that.asInt
  }

  object Level {

    case object Trace extends Level {
      protected val asInt = 0
    }

    case object Debug extends Level {
      protected val asInt = 10
    }

    case object Info extends Level {
      protected val asInt = 20
    }

    case object Warn extends Level {
      protected val asInt = 30
    }

    case object Error extends Level {
      protected val asInt = 40
    }

    case object Crit extends Level {
      protected val asInt = 50
    }

  }

  type LogContextEntry = (String, Any)
  type LogContext = Seq[LogContextEntry]

  //type LogContext = Map[String, Any]


  case class CustomContext(values: LogContext) {
    def +(that: CustomContext): CustomContext = {
      CustomContext(values ++ that.values)
    }
  }


  object CustomContext {
    def empty: CustomContext = CustomContext(List.empty)
  }

  case class LoggerId(id: String) extends AnyVal

  case class StaticExtendedContext(id: LoggerId, file: String, line: Int)

  case class ThreadData(threadName: String, threadId: Long)

  case class DynamicContext(level: Level, threadData: ThreadData, tsMillis: Long)

  case class Context(static: StaticExtendedContext, dynamic: DynamicContext, customContext: CustomContext)

  case class Entry(message: Message, context: Context)

  case class Message(template: StringContext, args: LogContext) {

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._

    def argsMap: Map[String, Set[Any]] = args.toMultimap
  }

}


