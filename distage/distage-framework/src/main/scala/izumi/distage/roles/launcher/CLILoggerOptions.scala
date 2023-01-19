package izumi.distage.roles.launcher

import distage.Id
import izumi.distage.roles.RoleAppMain
import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawEntrypointParams}
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Level

case class CLILoggerOptions(
  level: Level,
  json: Boolean,
)

trait CLILoggerOptionsReader {
  def read(): CLILoggerOptions
}

object CLILoggerOptionsReader {
  class CLILoggerOptionsReaderImpl(
    parameters: RawAppArgs,
    defaultLogLevel: Log.Level @Id("early"),
    defaultLogFormatJson: Boolean @Id("distage.roles.logs.json"),
  ) extends CLILoggerOptionsReader {
    override def read(): CLILoggerOptions = {
      val rootLogLevel = getRootLogLevel(parameters.globalParameters, defaultLogLevel)
      val logJson = getLogFormatJson(parameters.globalParameters, defaultLogFormatJson)

      CLILoggerOptions(rootLogLevel, logJson)
    }

    private def getRootLogLevel(parameters: RawEntrypointParams, defaultLogLevel: Log.Level): Level = {
      parameters
        .findValue(RoleAppMain.Options.logLevelRootParam)
        .map(v => Log.Level.parseSafe(v.value, defaultLogLevel))
        .getOrElse(defaultLogLevel)
    }

    private def getLogFormatJson(parameters: RawEntrypointParams, defaultLogFormatJson: Boolean): Boolean = {
      parameters
        .findValue(RoleAppMain.Options.logFormatParam)
        .map(_.value == "json")
        .getOrElse(defaultLogFormatJson)
    }
  }

}
