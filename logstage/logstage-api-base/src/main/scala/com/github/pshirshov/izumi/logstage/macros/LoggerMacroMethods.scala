package com.github.pshirshov.izumi.logstage.macros

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.{AbstractLogger, Log}
import com.github.pshirshov.izumi.logstage.macros.LogMessageMacro._

import scala.reflect.macros.blackbox

object LoggerMacroMethods {

  def scTraceMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    reify(c.prefix.splice.log(Log.Level.Trace)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scDebugMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    reify(c.prefix.splice.log(Log.Level.Debug)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scInfoMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    reify(c.prefix.splice.log(Log.Level.Info)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scWarnMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    reify(c.prefix.splice.log(Log.Level.Warn)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scErrorMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    reify(c.prefix.splice.log(Log.Level.Error)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scCritMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    reify(c.prefix.splice.log(Log.Level.Crit)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

//def scTraceMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
//  logMacro(c)(c.universe.reify(Log.Level.Trace), logMessageMacro(c)(message), mkPos(c))
//}
//
//def scDebugMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
//  logMacro(c)(c.universe.reify(Log.Level.Debug), logMessageMacro(c)(message), mkPos(c))
//}
//
//def scInfoMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
//  logMacro(c)(c.universe.reify(Log.Level.Info), logMessageMacro(c)(message), mkPos(c))
//}
//
//def scWarnMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
//  logMacro(c)(c.universe.reify(Log.Level.Warn), logMessageMacro(c)(message), mkPos(c))
//}
//
//def scErrorMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
//  logMacro(c)(c.universe.reify(Log.Level.Error), logMessageMacro(c)(message), mkPos(c))
//}
//
//def scCritMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
//  logMacro(c)(c.universe.reify(Log.Level.Crit), logMessageMacro(c)(message), mkPos(c))
//}

  @inline private[this] def mkPos(c: blackbox.Context): c.Expr[CodePositionMaterializer] = {
    CodePositionMaterializer.getEnclosingPosition(c)
  }

//  private def logMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Log.Level], message: c.Expr[Message], position: c.Expr[CodePositionMaterializer]): c.Expr[Unit] = {
//    c.universe.reify {
//      {
//        val self = c.prefix.splice
//        val pos = position.splice
//        if (self.acceptable(LoggerId(pos.get.applicationPointId), level.splice)) {
//          self.unsafeLog(level.splice)(message.splice)(pos)
//        }
//      }
//    }
//  }

}



