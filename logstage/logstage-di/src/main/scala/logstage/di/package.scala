package logstage

import com.github.pshirshov.izumi.logstage.distage

package object di extends LogstageDI {

  override type LogstageModule = distage.LogstageModule

}
