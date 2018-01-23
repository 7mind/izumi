package org.bitbucket.pshirshov.izumi.logger

import org.bitbucket.pshirshov.izumi.logger.Log.LogMapping

trait LogConfigService { // for now we may implement that even in code

  def config(e: Log.Entry): Seq[LogMapping]
}
