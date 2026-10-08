package izumi.functional.bio

import izumi.fundamentals.platform.uuid.IzUUID
import java.util.UUID

private[functional] object ThreadPlatform {
  def parentGroup: ThreadGroup = Thread.currentThread().getThreadGroup

  def newThreadId(): UUID = IzUUID.generateRandomUUID()
}
