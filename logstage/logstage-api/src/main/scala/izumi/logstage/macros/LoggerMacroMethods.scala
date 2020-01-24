package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.CodePositionMaterializer.getEnclosingPosition
import izumi.logstage.api.{AbstractLogger, Log}

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
  
  @inline private[this] def logMacro(c: blackbox.Context {type PrefixType = AbstractLogger})(level: c.Expr[Log.Level], message: c.Expr[Log.Message], position: c.Expr[CodePositionMaterializer]): c.Expr[Unit] = {
    c.universe.reify {
      {
        val self = c.prefix.splice
//        import c.universe._
//
        if (self.acceptable(Log.LoggerId(CodePositionMaterializer.getApplicationPointId(c).splice), level.splice)) {
          self.unsafeLog(Log.Entry.create(level.splice, message.splice)(position.splice))
        }
      }
    }
  }

}



