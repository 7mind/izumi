package izumi.thirdparty.internal.boopickle

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

private[izumi] object BufferPool {

  // two pools for two different size categories
  private final val poolEntrySize0 = ByteBufferProvider.initSize
  private final val poolEntrySize1 = ByteBufferProvider.expandSize + 16
  // maximum size of a ByteBuffer to be included in a pool
  private final val maxBufferSize = poolEntrySize1 * 2
  private final val entryCount    = 1024

  private var disablePool = false

  final class Pool {
    private val pool0       = new Array[ByteBuffer](entryCount)
    private val pool1       = new Array[ByteBuffer](entryCount)
    private val allocIdx0   = new AtomicInteger(0)
    private val allocIdx1   = new AtomicInteger(0)
    private val releaseIdx0 = new AtomicInteger(0)
    private val releaseIdx1 = new AtomicInteger(0)

    // for collecting some performance characteristics
    var allocOk   = 0
    var allocMiss = 0

    def allocate(minSize: Int): Option[ByteBuffer] = {
      if (disablePool) {
        None
      } else if (minSize > poolEntrySize1) {
        allocMiss += 1
        None
      } else if (minSize > poolEntrySize0 || allocIdx0.get() == releaseIdx0.get()) {
        // allocate from pool1
        val aIdx  = allocIdx1.get()
        val rIdx  = releaseIdx1.get()
        val aNext = (aIdx + 1) % entryCount
        if (aIdx != rIdx) {
          // try to allocate
          val result = Some(pool1(aNext))
          if (allocIdx1.compareAndSet(aIdx, aNext)) {
            allocOk += 1
            result
          } else {
            allocMiss += 1
            None
          }
        } else {
          allocMiss += 1
          None
        }
      } else {
        // allocate from pool0
        val aIdx  = allocIdx0.get()
        val rIdx  = releaseIdx0.get()
        val aNext = (aIdx + 1) % entryCount
        if (aIdx != rIdx) {
          // try to allocate
          val result = Some(pool0(aNext))
          if (allocIdx0.compareAndSet(aIdx, aNext)) {
            allocOk += 1
            result
          } else {
            allocMiss += 1
            None
          }
        } else {
          allocMiss += 1
          None
        }
      }
    }

    def release(bb: ByteBuffer): Unit = {
      if (!disablePool) {
        // do not take large buffers into the pool, as their reallocation is relatively cheap
        val bufSize = bb.capacity
        if (bufSize < maxBufferSize && bufSize >= poolEntrySize0) {
          if (bufSize >= poolEntrySize1) {
            val aIdx  = allocIdx1.get()
            val rIdx  = releaseIdx1.get()
            val rNext = (rIdx + 1) % entryCount
            if (rNext != aIdx) {
              // try to release the buffer
              (bb: java.nio.Buffer).clear()
              pool1(rNext) = bb
              releaseIdx1.compareAndSet(rIdx, rNext)
              ()
            }
          } else {
            val aIdx  = allocIdx0.get()
            val rIdx  = releaseIdx0.get()
            val rNext = (rIdx + 1) % entryCount
            if (rNext != aIdx) {
              // try to release the buffer
              (bb: java.nio.Buffer).clear()
              pool0(rNext) = bb
              releaseIdx0.compareAndSet(rIdx, rNext)
              ()
            }
          }
        }
      }
    }
  }

  val heapPool   = new Pool
  val directPool = new Pool

  def allocate(minSize: Int): Option[ByteBuffer] = {
    heapPool.allocate(minSize)
  }

  def allocateDirect(minSize: Int): Option[ByteBuffer] = {
    directPool.allocate(minSize)
  }

  def release(bb: ByteBuffer): Unit = {
    if (bb.isDirect)
      directPool.release(bb)
    else
      heapPool.release(bb)
  }

  def isDisabled = disablePool

  def disable(): Unit = disablePool = true

  def enable(): Unit = disablePool = false

  def allocOk   = heapPool.allocOk + directPool.allocOk
  def allocMiss = heapPool.allocMiss + directPool.allocMiss
}
