package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Message
import izumi.logstage.api.logger.AbstractLogger

import scala.reflect.macros.blackbox

object LoggerMacroMethods {

  def scTraceMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Trace), new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scDebugMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Debug), new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scInfoMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Info), new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scWarnMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Warn), new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scErrorMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Error), new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scCritMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Crit), new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scTraceMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Trace), new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scDebugMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Debug), new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scInfoMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Info), new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scWarnMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Warn), new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scErrorMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Error), new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scCritMacroStrict(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Crit), new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message), getEnclosingPosition(c))
  }

  def scTraceMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Trace), c.universe.reify(Message.raw(message.splice)), getEnclosingPosition(c))
  }

  def scDebugMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Debug), c.universe.reify(Message.raw(message.splice)), getEnclosingPosition(c))
  }

  def scInfoMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Info), c.universe.reify(Message.raw(message.splice)), getEnclosingPosition(c))
  }

  def scWarnMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Warn), c.universe.reify(Message.raw(message.splice)), getEnclosingPosition(c))
  }

  def scErrorMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Error), c.universe.reify(Message.raw(message.splice)), getEnclosingPosition(c))
  }

  def scCritMacroRaw(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Crit), c.universe.reify(Message.raw(message.splice)), getEnclosingPosition(c))
  }

  @inline private[this] def logMacro(
    c: blackbox.Context { type PrefixType = AbstractLogger }
  )(level: c.Expr[Log.Level],
    message: c.Expr[Log.Message],
    position: c.Expr[CodePositionMaterializer],
  ): c.Expr[Unit] = {
    c.universe.reify {
      {
        val self = c.prefix.splice
        if (self.acceptable(Log.LoggerId(CodePositionMaterializerMacro.getApplicationPointId(c).splice), level.splice)) {
          self.unsafeLog(Log.Entry.create(level.splice, message.splice)(position.splice))
        }
      }
    }
  }

}
