package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._

object Log {

  sealed trait Level extends Ordered[Level] {
    protected def asInt: Int
    protected def asString: String
    override def compare(that: Level): Int = this.asInt - that.asInt
  }

  object Level {

    def all : Set[Level] = Set(Info, Warn, Trace, Crit, Debug, Error)

    private final val labelMap =
      all.map(l => l.toString.toLowerCase -> l).toMap

    def parse(lvl:String): Level =
      labelMap.getOrElse(lvl.toLowerCase, {
        throw new IllegalArgumentException(s"Unknown log level $lvl")
      })

    case object Trace extends Level {
      protected val asInt = 0
      protected val asString: String = "trace"
    }

    case object Debug extends Level {
      protected val asInt = 10
      protected val asString: String = "debug"
    }

    case object Info extends Level {
      protected val asInt = 20
      protected val asString: String = "info"
    }

    case object Warn extends Level {
      protected val asInt = 30
      protected val asString: String = "warn"
    }

    case object Error extends Level {
      protected val asInt = 40
      protected val asString: String = "error"
    }

    case object Crit extends Level {
      protected val asInt = 50
      protected val asString: String = "crit"
    }

  }

  final case class LogArg(path: Seq[String], value: Any, hidden: Boolean) {
    def name: String = path.last
  }

  type LogContext = Seq[LogArg]

  final case class CustomContext(values: LogContext) {
    def +(that: CustomContext): CustomContext = {
      CustomContext(values ++ that.values)
    }
  }

  object CustomContext {
    def empty: CustomContext = CustomContext(List.empty)
  }

  final case class LoggerId(id: String) extends AnyVal

  final case class StaticExtendedContext(id: LoggerId, position: SourceFilePosition)

  final case class ThreadData(threadName: String, threadId: Long)

  final case class DynamicContext(level: Level, threadData: ThreadData, tsMillis: Long)

  final case class Context(static: StaticExtendedContext, dynamic: DynamicContext, customContext: CustomContext)

  final case class Entry(message: Message, context: Context) {
    def firstThrowable: Option[Throwable] = {
      message.args.map(_.value).collectFirst { case t: Throwable => t }
    }
  }

  final case class Message(template: StringContext, args: LogContext) {
    def argsMap: Map[String, Set[Any]] = args.map(kv => (kv.name, kv.value)).toMultimap
  }

}


