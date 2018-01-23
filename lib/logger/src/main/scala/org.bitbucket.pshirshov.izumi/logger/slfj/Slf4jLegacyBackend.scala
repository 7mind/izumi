package org.bitbucket.pshirshov.izumi.logger.slfj

import org.slf4j.{Logger, LoggerFactory}

trait Slf4jLegacyBackend extends Logger {
  // here we should implement slf4j backend which would route plain text messages into LogRouter
  // Don't forget that slf4j message may carry an exception: `log.error(s"My message", exception)`

  import java.lang.invoke.MethodHandles

  val logger = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)


}