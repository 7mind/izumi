package com.github.pshirshov.izumi.logstage.api

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


  final case class CustomContext(values: LogContext) {
    def +(that: CustomContext): CustomContext = {
      CustomContext(values ++ that.values)
    }
  }


  object CustomContext {
    def empty: CustomContext = CustomContext(List.empty)
  }

  final case class LoggerId(id: String) extends AnyVal

  final case class StaticExtendedContext(id: LoggerId, file: String, line: Int)

  final case class ThreadData(threadName: String, threadId: Long)

  final case class DynamicContext(level: Level, threadData: ThreadData, tsMillis: Long)

  final case class Context(static: StaticExtendedContext, dynamic: DynamicContext, customContext: CustomContext)

  final case class Entry(message: Message, context: Context) {
    def firstThrowable: Option[Throwable] = {
      message.args.map(_._2).collectFirst { case t: Throwable => t }
    }
  }

  final case class Message(template: StringContext, args: LogContext) {

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._

    def argsMap: Map[String, Set[Any]] = args.toMultimap
  }

}


