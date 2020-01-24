package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.getEnclosingPosition
import logstage.{Log, LogIO}

import scala.reflect.macros.blackbox

object LogIOMacroMethods {
  def scTraceMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Trace)(new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scDebugMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Debug)(new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scInfoMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Info)(new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scWarnMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Warn)(new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scErrorMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Error)(new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scCritMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Crit)(new LogMessageMacro0[c.type](c, strict = false).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scTraceMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Trace)(new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scDebugMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Debug)(new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scInfoMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Info)(new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scWarnMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Warn)(new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scErrorMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Error)(new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }

  def scCritMacroStrict[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Crit)(new LogMessageMacro0[c.type](c, strict = true).logMessageMacro(message).splice)(getEnclosingPosition(c).splice))
  }  
}
