package com.github.pshirshov.izumi.logstage.macros

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.{AbstractLogger, Log}
import com.github.pshirshov.izumi.logstage.macros.LogMessageMacro._

import scala.reflect.macros.blackbox

object LoggerMacroMethods {

  def scTraceMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    val logLevel = reify(Log.Level.Trace)
    reify(c.prefix.splice.log(logLevel.splice)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scDebugMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    val logLevel = reify(Log.Level.Debug)
    reify(c.prefix.splice.log(logLevel.splice)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scInfoMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    val logLevel = reify(Log.Level.Info)
    reify(c.prefix.splice.log(logLevel.splice)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scWarnMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    val logLevel = reify(Log.Level.Warn)
    reify(c.prefix.splice.log(logLevel.splice)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scErrorMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    val logLevel = reify(Log.Level.Error)
    reify(c.prefix.splice.log(logLevel.splice)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  def scCritMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.reify
    val logLevel = reify(Log.Level.Crit)
    reify(c.prefix.splice.log(logLevel.splice)(logMessageMacro(c)(message).splice)(mkPos(c).splice))
  }

  @inline private[this] def mkPos(c: blackbox.Context): c.Expr[CodePositionMaterializer] = {
    CodePositionMaterializer.getEnclosingPosition(c)
  }

}



