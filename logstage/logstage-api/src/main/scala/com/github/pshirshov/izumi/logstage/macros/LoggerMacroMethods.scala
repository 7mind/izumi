package com.github.pshirshov.izumi.logstage.macros

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer.getEnclosingPosition
import com.github.pshirshov.izumi.logstage.api.{AbstractLogger, Log}
import com.github.pshirshov.izumi.logstage.macros.LogMessageMacro._

import scala.reflect.macros.blackbox

object LoggerMacroMethods {

  def scTraceMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Trace), logMessageMacro(c)(message), getEnclosingPosition(c))
  }

  def scDebugMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Debug), logMessageMacro(c)(message), getEnclosingPosition(c))
  }

  def scInfoMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Info), logMessageMacro(c)(message), getEnclosingPosition(c))
  }

  def scWarnMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Warn), logMessageMacro(c)(message), getEnclosingPosition(c))
  }

  def scErrorMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Error), logMessageMacro(c)(message), getEnclosingPosition(c))
  }

  def scCritMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    logMacro(c)(c.universe.reify(Log.Level.Crit), logMessageMacro(c)(message), getEnclosingPosition(c))
  }

  @inline private[this] def logMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Log.Level], message: c.Expr[Log.Message], position: c.Expr[CodePositionMaterializer]): c.Expr[Unit] = {
    c.universe.reify {
      {
        val self = c.prefix.splice
        if (self.acceptable(Log.LoggerId(CodePositionMaterializer.getApplicationPointId(c).splice), level.splice)) {
          self.unsafeLog(Log.Entry.create(level.splice, message.splice)(position.splice))
        }
      }
    }
  }

}



