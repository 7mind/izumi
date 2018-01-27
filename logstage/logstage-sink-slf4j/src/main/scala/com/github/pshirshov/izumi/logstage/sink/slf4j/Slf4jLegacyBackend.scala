package com.github.pshirshov.izumi.logstage.sink.slf4j

import com.github.pshirshov.izumi.logstage.api.logger.RoutingLogReceiver


trait Slf4jLegacyBackend extends RoutingLogReceiver {
  // here we should implement slf4j backend which would route plain text messages into LogRouter
  // Don't forget that slf4j message may carry an exception: `log.error(s"My message", exception)`

  //val logger = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)


}