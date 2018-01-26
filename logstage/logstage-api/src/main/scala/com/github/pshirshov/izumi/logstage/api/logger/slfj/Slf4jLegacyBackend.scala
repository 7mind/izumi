package com.github.pshirshov.izumi.logstage.api.logger.slfj

import com.github.pshirshov.izumi.logstage.api.logger.api.Logger


trait Slf4jLegacyBackend extends Logger {
  // here we should implement slf4j backend which would route plain text messages into LogRouter
  // Don't forget that slf4j message may carry an exception: `log.error(s"My message", exception)`

  //val logger = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)


}