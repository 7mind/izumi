package izumi.logstage.api

import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer, SourceFilePosition}
import izumi.logstage.api.rendering.{AnyEncoded, LogstageCodec}
import izumi.logstage.macros.{LogMessageMacro, LogMessageMacroStrict}

import scala.language.experimental.macros
import scala.language.implicitConversions

object Log {

  sealed trait Level extends Ordered[Level] {
    override def compare(that: Level): Int = this.asInt - that.asInt
    protected def asInt: Int
  }

  object Level {

    def all : Set[Level] = Set(Info, Warn, Trace, Crit, Debug, Error)

    private final val labelMap =
      all.map(l => l.toString.toLowerCase -> l).toMap

    def parse(lvl:String): Level =
      labelMap.getOrElse(lvl.toLowerCase, {
        throw new IllegalArgumentException(s"Unknown log level $lvl")
      })

    def parseSafe(lvl:String, default: Level): Level = {
      labelMap
        .find { case (k, _) => k.toLowerCase.startsWith(lvl.toLowerCase) || lvl.toLowerCase.startsWith(k.toLowerCase) }
        .map(_._2)
        .getOrElse(default)
    }

    def parseLetter(v: String): Level = {
      v.charAt(0).toLower match {
        case 't' => Log.Level.Trace
        case 'd' => Log.Level.Debug
        case 'i' => Log.Level.Info
        case 'w' => Log.Level.Warn
        case 'e' => Log.Level.Error
        case 'c' => Log.Level.Crit
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

  }

  final case class LogArg(path: Seq[String], value: Any, hiddenName: Boolean, codec: Option[LogstageCodec[_]]) {
    def name: String = path.last
  }

  type LogContext = Seq[LogArg]

  final case class CustomContext(values: LogContext) {
    def +(that: CustomContext): CustomContext = CustomContext(values ++ that.values)
  }

  object CustomContext {
    def fromMap(map: Map[String, AnyEncoded]): CustomContext = {
      val logArgs = map.map {
        case (k, v) => LogArg(Seq(k), v.value, hiddenName = false, v.codec)
      }.toList

      CustomContext(logArgs)
    }

    def apply(args: (String, AnyEncoded)*)(implicit dummy: DummyImplicit): CustomContext = {
      CustomContext.fromMap(Map(args: _*))
    }

    val empty: CustomContext = CustomContext(Nil)
  }

  final case class LoggerId(id: String) extends AnyVal

  object LoggerId {
    @inline final def fromCodePosition(pos: CodePosition): LoggerId = {
      new LoggerId(pos.applicationPointId)
    }
  }

  final case class StaticExtendedContext(id: LoggerId, position: SourceFilePosition)

  final case class ThreadData(threadName: String, threadId: Long)

  final case class DynamicContext(level: Level, threadData: ThreadData, tsMillis: Long)

  final case class Context(
                            static: StaticExtendedContext
                          , dynamic: DynamicContext
                          , customContext: CustomContext
                          ) {
    def +(that: CustomContext): Context = {
      copy(customContext = customContext + that)
    }
  }

  object Context {
    /** Record surrounding source code location, current thread and timestamp */
    @inline final def recordContext(logLevel: Log.Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): Context = {
      val thread = Thread.currentThread()
      val tsMillis = System.currentTimeMillis()
      val dynamicContext = DynamicContext(logLevel, ThreadData(thread.getName, thread.getId), tsMillis)
      val extendedStaticContext = StaticExtendedContext(LoggerId(pos.get.applicationPointId), pos.get.position)

      Log.Context(extendedStaticContext, dynamicContext, customContext)
    }
  }

  final case class Entry(message: Message, context: Context) {
    def firstThrowable: Option[Throwable] = {
      message.args.map(_.value).collectFirst { case t: Throwable => t }
    }

    @inline def addCustomContext(ctx: CustomContext): Entry = {
      if (ctx.values.isEmpty) {
        this
      } else {
        copy(context = context.copy(customContext = context.customContext + ctx))
      }
    }
  }

  object Entry {
    /** Create an Entry recording a `message` along with current thread, timestamp and source code location */
    @inline final def create(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): Entry = {
      Log.Entry(message, Context.recordContext(logLevel, CustomContext.empty)(pos))
    }
  }

  final case class Message(template: StringContext, args: LogContext) {
    def argsMap: Map[String, Set[Any]] = args.map(kv => (kv.name, kv.value)).toMultimap

    def +(that: Message): Message = Message(StringContext(template.parts ++ that.template.parts: _*), args ++ that.args)
  }

  object Message {
    /** Construct [[Message]] from a string interpolation */
    implicit def apply(message: String): Message = macro LogMessageMacro.logMessageMacro
  }
  object StrictMessage {
    /** Construct [[Message]] from a string interpolation */
    implicit def apply(message: String): Message = macro LogMessageMacroStrict.logMessageMacro
  }
}
