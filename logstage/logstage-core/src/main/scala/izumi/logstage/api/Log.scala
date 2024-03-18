package izumi.logstage.api

import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer, SourceFilePosition}
import izumi.logstage.api.rendering.{AnyEncoded, LogstageCodec}

object Log {

  sealed trait Level extends Ordered[Level] {
    override def compare(that: Level): Int = this.asInt - that.asInt
    protected def asInt: Int
  }

  object Level {

    def all: Set[Level] = Set(Info, Warn, Trace, Crit, Debug, Error)

    private final val labelMap =
      all.map(l => l.toString.toLowerCase -> l).toMap

    def parse(lvl: String): Level =
      labelMap.getOrElse(lvl.toLowerCase, throw new IllegalArgumentException(s"Unknown log level $lvl"))

    def parseSafe(lvl: String, default: Level): Level = {
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

  final case class LogArgTyped[T](path: Seq[String], value: T, hiddenName: Boolean, codec: Option[LogstageCodec[T]]) {
    def name: String = path.last
  }

  type LogArg = LogArgTyped[Any]
  object LogArg {
    def apply[T](value: Seq[String], t: T, hiddenName: Boolean, codec: Option[LogstageCodec[T]]): LogArg = {
      LogArgTyped(value, t, hiddenName, codec.map(t => t.asInstanceOf[LogstageCodec[Any]]))
    }
  }
  type LogContext = Seq[LogArg]

  final case class CustomContext(values: LogContext) {
    def +(that: CustomContext): CustomContext = CustomContext(values ++ that.values)
  }

  object CustomContext {
    def fromMap(map: Map[String, AnyEncoded]): CustomContext = {
      val logArgs = map.map {
        case (k, v) => LogArg(Seq(k), v.value, hiddenName = false, v.codec.map(_.asInstanceOf[LogstageCodec[Any]]))
      }.toList

      CustomContext(logArgs)
    }

    def apply(args: (String, AnyEncoded)*)(implicit dummy: DummyImplicit): CustomContext = {
      CustomContext.fromMap(Map(args*))
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
    static: StaticExtendedContext,
    dynamic: DynamicContext,
    customContext: CustomContext,
  ) {
    def ++(that: CustomContext): Context = {
      copy(customContext = customContext + that)
    }
    def +(that: CustomContext): Context = ++(that)
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

    def throwables: Seq[LogArgTyped[Throwable]] = {
      message.args
        .map {
          a => (a.value, a)
        }
        .collect {
          case (t: Throwable, c) =>
            LogArgTyped(c.path, t, c.hiddenName, c.codec.map(_.asInstanceOf[LogstageCodec[Throwable]]))
        }
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

    def ++(that: Message): Message = {
      if (that.template.parts.isEmpty) this
      else if (template.parts.isEmpty) that
      else {
        // this ain't pretty but avoids multiple iterations on the same parts collection compared to using .init/.tail
        val (thisInit, thisTails) = template.parts.splitAt(template.parts.length - 1)
        val thisTail = thisTails.head

        val (thatHeads, thatTail) = that.template.parts.splitAt(1)
        val thatHead = thatHeads.head

        val parts = (thisInit :+ (thisTail + thatHead)) ++ thatTail
        Message(StringContext(parts*), args ++ that.args)
      }
    }
    def +(that: Message): Message = ++(that)
  }
  /** Construct [[Message]] from a string interpolation using [[Message.apply]] */
  object Message extends MessageMat {
    def raw(message: String): Message = Message(StringContext(message), Nil)

    def empty: Message = raw("")
  }

  /** Construct [[Message]] from a string interpolation using [[StrictMessage.apply]] */
  object StrictMessage extends StrictMessageMat {
    def empty: Message = Message.empty
  }
}
