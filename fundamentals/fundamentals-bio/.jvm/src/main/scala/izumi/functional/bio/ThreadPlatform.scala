package izumi.functional.bio

import java.util.UUID
import scala.annotation.nowarn

private[functional] object ThreadPlatform {
  @nowarn("msg=deprecated")
  def parentGroup: ThreadGroup = Option(System.getSecurityManager).fold(Thread.currentThread().getThreadGroup)(_.getThreadGroup)

  def newThreadId(): UUID = UUID.randomUUID()
}
