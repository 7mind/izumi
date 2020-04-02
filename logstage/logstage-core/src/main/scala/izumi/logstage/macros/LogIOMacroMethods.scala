package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.logstage.api.Log.{Level, Message}
import logstage.{Log, LogIO}

import scala.reflect.macros.blackbox

object LogIOMacroMethods {
  def scTraceMacro[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Trace, strict = false)
  }

  def scDebugMacro[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Debug, strict = false)
  }

  def scInfoMacro[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Info, strict = false)
  }

  def scWarnMacro[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Warn, strict = false)
  }

  def scErrorMacro[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Error, strict = false)
  }

  def scCritMacro[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Crit, strict = false)
  }

  def scTraceMacroStrict[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Trace, strict = true)
  }

  def scDebugMacroStrict[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Debug, strict = true)
  }

  def scInfoMacroStrict[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Info, strict = true)
  }

  def scWarnMacroStrict[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Warn, strict = true)
  }

  def scErrorMacroStrict[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Error, strict = true)
  }

  def scCritMacroStrict[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Log.Level.Crit, strict = true)
  }

  private[this] def doLog[F[_]](c: blackbox.Context {type PrefixType = LogIO[F]})(message: c.Expr[String], level: Level, strict: Boolean): c.universe.Expr[F[Unit]] = {
    val m: c.Expr[Message] = new LogMessageMacro0[c.type](c, strict = strict).logMessageMacro(message)
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
}
