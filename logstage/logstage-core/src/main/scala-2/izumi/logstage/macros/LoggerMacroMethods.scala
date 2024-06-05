package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.{getApplicationPointId, getEnclosingPosition}
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.AbstractLogger

import scala.reflect.macros.blackbox

object LoggerMacroMethods {

  def scTraceMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Trace, EncodingMode.NonStrict)
  }

  def scDebugMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Debug, EncodingMode.NonStrict)
  }

  def scInfoMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Info, EncodingMode.NonStrict)
  }

  def scWarnMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Warn, EncodingMode.NonStrict)
  }

  def scErrorMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Error, EncodingMode.NonStrict)
  }

  def scCritMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Crit, EncodingMode.NonStrict)
  }

  def scTraceMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Trace, EncodingMode.Strict)
  }

  def scDebugMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Debug, EncodingMode.Strict)
  }

  def scInfoMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Info, EncodingMode.Strict)
  }

  def scWarnMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Warn, EncodingMode.Strict)
  }

  def scErrorMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Error, EncodingMode.Strict)
  }

  def scCritMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Crit, EncodingMode.Strict)
  }

  def scTraceMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Trace, EncodingMode.Raw)
  }

  def scDebugMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Debug, EncodingMode.Raw)
  }

  def scInfoMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Info, EncodingMode.Raw)
  }

  def scWarnMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Warn, EncodingMode.Raw)
  }

  def scErrorMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Error, EncodingMode.Raw)
  }

  def scCritMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Crit, EncodingMode.Raw)
  }

  private def doLog(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String], level: Level, mode: EncodingMode): c.Expr[Unit] = {
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
      val self = c.prefix.splice
      if (self.acceptable(Log.LoggerId(getApplicationPointId(c).splice), l.splice)) {
        self.unsafeLog(Log.Entry.create(l.splice, m.splice)(getEnclosingPosition(c).splice))
      }
    }
  }

}
