package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
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

  def scLogValues(c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Level])(values: c.Expr[Any]*): c.Expr[Unit] = {
    doLogValues(c)(level, values, EncodingMode.NonStrict)
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

  def scLogValuesStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Level])(values: c.Expr[Any]*): c.Expr[Unit] = {
    doLogValues(c)(level, values, EncodingMode.Strict)
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

  def scLogValuesRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Level])(values: c.Expr[Any]*): c.Expr[Unit] = {
    doLogValues(c)(level, values, EncodingMode.Raw)
  }

  private def doLog(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String], level: Level, mode: EncodingMode): c.Expr[Unit] = {
    val m = LogMessageMacro0.createMessageWithMode(c)(message, mode)
    val l = LogMessageMacro0.reifyLevel(c)(level)
    doLogImpl(c)(m, l)
  }

  private def doLogValues(
    c: blackbox.Context { type PrefixType = AbstractLogger }
  )(level: c.Expr[Level],
    values: Seq[c.Expr[Any]],
    mode: EncodingMode,
  ): c.Expr[Unit] = {
    val message = LogValuesMacro.createMessageString(c)(values)
    val m = LogMessageMacro0.createMessageWithMode(c)(message, mode)
    doLogImpl(c)(m, level)
  }

  private def doLogImpl(
    c: blackbox.Context { type PrefixType = AbstractLogger }
  )(message: c.Expr[Message],
    level: c.Expr[Level],
  ): c.Expr[Unit] = {
    c.universe.reify {
      val self = c.prefix.splice
      val position = getEnclosingPosition(c).splice
      if (self.acceptable(position.get, level.splice)) {
        self.unsafeLog(Log.Entry.create(level.splice, message.splice)(position))
      }
    }
  }

}
