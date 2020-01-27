package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.logstage.macros.LogMessageMacro.logMessageMacro
import logstage.{Log, LogIO}

import scala.reflect.macros.blackbox

object LogIOMacros {
  def scTraceMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Trace)(logMessageMacro(c)(message).splice)(getEnclosingPosition(c).splice))
  }

  def scDebugMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Debug)(logMessageMacro(c)(message).splice)(getEnclosingPosition(c).splice))
  }

  def scInfoMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Info)(logMessageMacro(c)(message).splice)(getEnclosingPosition(c).splice))
  }

  def scWarnMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Warn)(logMessageMacro(c)(message).splice)(getEnclosingPosition(c).splice))
  }

  def scErrorMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Error)(logMessageMacro(c)(message).splice)(getEnclosingPosition(c).splice))
  }

  def scCritMacro[F[_]](c: blackbox.Context { type PrefixType = LogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    c.universe.reify(c.prefix.splice.log(Log.Level.Crit)(logMessageMacro(c)(message).splice)(getEnclosingPosition(c).splice))
  }
}
