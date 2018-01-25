package org.bitbucket.pshirshov.izumi.logger

import com.ratoshniuk.izumi.Log

trait LogConfigService { // for now we may implement that even in code

  def config(e: Log.Entry): Seq[LogMapping]
}
