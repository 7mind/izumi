package izumi.logstage.macros

import izumi.fundamentals.platform.language.CodePositionMaterializer.CodePositionMaterializerMacro
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.logger.{AbstractLogger, AbstractMacroLogger}
import izumi.logstage.macros.EncodingModeExtractors.{getModeFromPrefixesEncModeTypeMember, getModeFromType}

import scala.reflect.macros.blackbox

object LoggerMacroMethods {

  def scTraceMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Trace)
  }

  def scDebugMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Debug)
  }

  def scInfoMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Info)
  }

  def scWarnMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Warn)
  }

  def scErrorMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Error)
  }

  def scCritMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Crit)
  }
  def scAuditMacro(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String]): c.Expr[Unit] = {
    doLog(c)(message, Level.Audit)
  }

  def scLogValues(c: blackbox.Context { type PrefixType = AbstractLogger })(level: c.Expr[Level])(values: c.Expr[Any]*): c.Expr[Unit] = {
    doLogValues(c)(level, values)
  }

  def scLogMethod[A, EncMode: c.WeakTypeTag](c: blackbox.Context { type PrefixType = AbstractMacroLogger.LogMethod[EncMode] })(function: c.Expr[A]): c.Expr[A] = {
    import c.universe.*
    val mode = getModeFromType[EncMode](c)
    val prefixName = c.freshName(TermName("prefix"))
    val self = c.Expr[AbstractLogger](q"$prefixName.__getSelf")
    val level = c.Expr[Level](q"$prefixName.__getSelfLevel")
    val printTypes = c.Expr[Boolean](q"$prefixName.__printTypes")
    val printImplicits = c.Expr[Boolean](q"$prefixName.__printImplicits")

    new LogMethodMacro[c.type](c).logMethod[A](mode, prefixName, self, level, printTypes, printImplicits, function)
  }

  private def doLog(c: blackbox.Context { type PrefixType = AbstractLogger })(message: c.Expr[String], level: Level): c.Expr[Unit] = {
    val mode = getModeFromPrefixesEncModeTypeMember(c)
    val m = LogMessageMacro.createMessageWithMode(c)(message, mode)
    val l = LogMessageMacro.reifyLevel(c)(level)
    doLogImpl(c)(m, l)
  }

  private def doLogValues(
    c: blackbox.Context { type PrefixType = AbstractLogger }
  )(level: c.Expr[Level],
    values: Seq[c.Expr[Any]],
  ): c.Expr[Unit] = {
    val mode = getModeFromPrefixesEncModeTypeMember(c)
    val message = LogValuesMacro.createMessageString(c)(values)
    val m = LogMessageMacro.createMessageWithMode(c)(message, mode)
    doLogImpl(c)(m, level)
  }

  private def doLogImpl(
    c: blackbox.Context { type PrefixType = AbstractLogger }
  )(message: c.Expr[Message],
    level: c.Expr[Level],
  ): c.Expr[Unit] = {
    c.universe.reify {
      val self = c.prefix.splice
      val position = CodePositionMaterializerMacro.getEnclosingPosition(c).splice
      if (self.acceptable(position.get, level.splice)) {
        self.unsafeLog(Log.Entry.create(level.splice, message.splice)(position))
      }
    }
  }

}
