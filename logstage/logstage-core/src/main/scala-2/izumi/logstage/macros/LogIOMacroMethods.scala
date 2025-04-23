package izumi.logstage.macros

import izumi.functional.quasi.{QuasiIO, QuasiPrimitives}
import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro.getEnclosingPosition
import izumi.fundamentals.reflection.ReflectionUtil
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.AbstractLogIO

import scala.reflect.macros.blackbox

object LogIOMacroMethods {
  def scTraceMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Trace, EncodingMode.NonStrict)
  }

  def scDebugMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Debug, EncodingMode.NonStrict)
  }

  def scInfoMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Info, EncodingMode.NonStrict)
  }

  def scWarnMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Warn, EncodingMode.NonStrict)
  }

  def scErrorMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Error, EncodingMode.NonStrict)
  }

  def scCritMacro[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Crit, EncodingMode.NonStrict)
  }

  def scLogMethod[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level]
  )(function: c.Expr[A]
  )(qp: c.Expr[QuasiIO[F]]
  ): c.Expr[F[A]] = {
    scLogMethodImpl[F, A](c)(level, printTypes = true, printImplicits = true)(function)(qp)
  }

  def scLogMethodF[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level]
  )(function: c.Expr[F[A]]
  )(qp: c.Expr[QuasiPrimitives[F]]
  ): c.Expr[F[A]] = {
    scLogMethodFImpl[F, A](c)(level, printTypes = true, printImplicits = true)(function)(qp)
  }

  def scLogMethodPrintTypes[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level],
    printTypes: c.Expr[Boolean],
  )(function: c.Expr[A]
  )(qp: c.Expr[QuasiIO[F]]
  ): c.Expr[F[A]] = {
    scLogMethodImpl[F, A](c)(level, ReflectionUtil.getBooleanLiteral(c)(printTypes.tree), printImplicits = true)(function)(qp)
  }

  def scLogMethodFPrintTypes[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level],
    printTypes: c.Expr[Boolean],
  )(function: c.Expr[F[A]]
  )(qp: c.Expr[QuasiPrimitives[F]]
  ): c.Expr[F[A]] = {
    scLogMethodFImpl[F, A](c)(level, ReflectionUtil.getBooleanLiteral(c)(printTypes.tree), printImplicits = true)(function)(qp)
  }

  def scLogMethodPrintTypesImplicits[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level],
    printTypes: c.Expr[Boolean],
    printImplicits: c.Expr[Boolean],
  )(function: c.Expr[A]
  )(qp: c.Expr[QuasiIO[F]]
  ): c.Expr[F[A]] = {
    scLogMethodImpl[F, A](c)(
      level,
      ReflectionUtil.getBooleanLiteral(c)(printTypes.tree),
      ReflectionUtil.getBooleanLiteral(c)(printImplicits.tree),
    )(function)(qp)
  }

  def scLogMethodFPrintTypesImplicits[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level],
    printTypes: c.Expr[Boolean],
    printImplicits: c.Expr[Boolean],
  )(function: c.Expr[F[A]]
  )(qp: c.Expr[QuasiPrimitives[F]]
  ): c.Expr[F[A]] = {
    scLogMethodFImpl[F, A](c)(
      level,
      ReflectionUtil.getBooleanLiteral(c)(printTypes.tree),
      ReflectionUtil.getBooleanLiteral(c)(printImplicits.tree),
    )(function)(qp)
  }

  private def scLogMethodImpl[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level],
    printTypes: Boolean,
    printImplicits: Boolean,
  )(function: c.Expr[A]
  )(qp: c.Expr[QuasiIO[F]]
  ): c.Expr[F[A]] = {
    new LogMethodMacro[c.type](c).logMethodIO[F, A](
      level,
      function,
      printTypes,
      printImplicits,
      qp,
    )
  }

  private def scLogMethodFImpl[F[_], A](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(level: c.Expr[Level],
    printTypes: Boolean,
    printImplicits: Boolean,
  )(function: c.Expr[F[A]]
  )(qp: c.Expr[QuasiPrimitives[F]]
  ): c.Expr[F[A]] = {
    new LogMethodMacro[c.type](c).logMethodIOF[F, A](
      level,
      function,
      printTypes,
      printImplicits,
      qp,
    )
  }

  def scTraceMacroStrict[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Trace, EncodingMode.Strict)
  }

  def scDebugMacroStrict[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Debug, EncodingMode.Strict)
  }

  def scInfoMacroStrict[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Info, EncodingMode.Strict)
  }

  def scWarnMacroStrict[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Warn, EncodingMode.Strict)
  }

  def scErrorMacroStrict[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Error, EncodingMode.Strict)
  }

  def scCritMacroStrict[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Crit, EncodingMode.Strict)
  }

  def scTraceMacroRaw[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Trace, EncodingMode.Raw)
  }

  def scDebugMacroRaw[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Debug, EncodingMode.Raw)
  }

  def scInfoMacroRaw[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Info, EncodingMode.Raw)
  }

  def scWarnMacroRaw[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Warn, EncodingMode.Raw)
  }

  def scErrorMacroRaw[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Error, EncodingMode.Raw)
  }

  def scCritMacroRaw[F[_]](c: blackbox.Context { type PrefixType = AbstractLogIO[F] })(message: c.Expr[String]): c.Expr[F[Unit]] = {
    doLog(c)(message, Level.Crit, EncodingMode.Raw)
  }

  private def doLog[F[_]](
    c: blackbox.Context { type PrefixType = AbstractLogIO[F] }
  )(message: c.Expr[String],
    level: Level,
    mode: EncodingMode,
  ): c.universe.Expr[F[Unit]] = {
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
      c.prefix.splice.log(l.splice)(m.splice)(getEnclosingPosition(c).splice)
    }
  }

}
