package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.logstage.api.Log.{Level, Message}
import logstage.{Log, LogIO}

import scala.reflect.macros.blackbox

object LogIOMacroMethods {
  def scTraceMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Trace, Mode.NonStrict)
  }

  def scDebugMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Debug, Mode.NonStrict)
  }

  def scInfoMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Info, Mode.NonStrict)
  }

  def scWarnMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Warn, Mode.NonStrict)
  }

  def scErrorMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Error, Mode.NonStrict)
  }

  def scCritMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Crit, Mode.NonStrict)
  }

  def scTraceMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Trace, Mode.Strict)
  }

  def scDebugMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Debug, Mode.Strict)
  }

  def scInfoMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Info, Mode.Strict)
  }

  def scWarnMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Warn, Mode.Strict)
  }

  def scErrorMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Error, Mode.Strict)
  }

  def scCritMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Crit, Mode.Strict)
  }

  def scTraceMacroRaw[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Trace, Mode.Raw)
  }

  def scDebugMacroRaw[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Debug, Mode.Raw)
  }

  def scInfoMacroRaw[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Info, Mode.Raw)
  }

  def scWarnMacroRaw[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Warn, Mode.Raw)
  }

  def scErrorMacroRaw[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Error, Mode.Raw)
  }

  def scCritMacroRaw[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Crit, Mode.Raw)
  }

  private[this] def doLog[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String], level: Level, mode: Mode): c.universe.Expr[F[Unit]] = {
    val m: c.Expr[Message] = mode.fold(c.universe.reify(Message.raw(message.splice))) {
      strict =>
        new LogMessageMacro0[c.type](c, strict = strict).logMessageMacro(message)
    }
    val l = level match {
      case Level.Trace =>
        c.universe.reify(Level.Trace)
      case Level.Debug =>
        c.universe.reify(Level.Debug)
      case Level.Info =>
        c.universe.reify(Level.Info)
      case Level.Warn =>
        c.universe.reify(Level.Warn)
      case Level.Error =>
        c.universe.reify(Level.Error)
      case Level.Crit =>
        c.universe.reify(Level.Crit)
    }

    c.universe.reify {
      c.prefix.splice.log(l.splice)(m.splice)(getEnclosingPosition(c).splice)
    }
  }

  private sealed trait Mode {
    final def fold[R](onRaw: => R)(onStrictness: Boolean => R): R = this match {
      case Mode.NonStrict => onStrictness(false)
      case Mode.Strict => onStrictness(true)
      case Mode.Raw => onRaw
    }
  }
  private object Mode {
    case object NonStrict extends Mode
    case object Strict extends Mode
    case object Raw extends Mode
  }
}
