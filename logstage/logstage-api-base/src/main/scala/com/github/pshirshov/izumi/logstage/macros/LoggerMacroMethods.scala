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

  @inline private[this] def mkPos(c: blackbox.Context): c.Expr[CodePositionMaterializer] = {
    CodePositionMaterializer.getEnclosingPosition(c)
  }

}



