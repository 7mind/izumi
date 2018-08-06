package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition

object Log {

  sealed trait Level extends Ordered[Level] {
    protected def asInt: Int

    override def compare(that: Level): Int = this.asInt - that.asInt
  }

  object Level {

    def parse(lvl: String) : Level = {
      lvl.toLowerCase match {
        case s if s == info => Info
        case s if s == warn => Warn
        case s if s == trace => Trace
        case s if s == crit => Crit
        case s if s == trace => Trace
        case s if s == debug => Debug
        case unknown => throw new IllegalArgumentException(s"Unknown log level label: $unknown. Possible are: ${allLabels.mkString(", ")}")
      }
    }

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

    private[this] final val info = "info"
    private[this] final val warn = "warn"
    private[this] final val trace = "trace"
    private[this] final val debug = "debug"
    private[this] final val error = "error"
    private[this] final val crit = "crit"

    private[this] val allLabels : Set[String] = Set(info, warn, trace, debug, error, crit)

  }

  case class LogArg(path: Seq[String], value: Any, hidden: Boolean) {
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

    import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._

    def argsMap: Map[String, Set[Any]] = args.map(kv => (kv.name, kv.value)).toMultimap
  }

}


