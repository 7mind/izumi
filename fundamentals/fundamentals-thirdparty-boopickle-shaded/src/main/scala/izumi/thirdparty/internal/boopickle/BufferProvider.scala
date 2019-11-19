package izumi.thirdparty.internal.boopickle

import java.nio.{ByteBuffer, ByteOrder}

private[izumi] trait BufferProvider {

  /**
    * Makes sure the ByteBuffer has enough space for new data. If not, allocates a new ByteBuffer
    * and returns it. The returned ByteBuffer must have little-endian ordering.
    *
    * @param size Number of bytes needed for new data
    * @return
    */
  def alloc(size: Int): ByteBuffer

  /**
    * Completes the encoding and returns the ByteBuffer, merging the chain of buffers if necessary
    *
    * @return
    */
  def asByteBuffer: ByteBuffer

  /**
    * Completes the encoding and returns a sequence of ByteBuffers
    *
    * @return
    */
  def asByteBuffers: Iterable[ByteBuffer]
}

private[izumi] abstract class ByteBufferProvider extends BufferProvider {
  import ByteBufferProvider._
  protected val pool                      = BufferPool
  protected var buffers: List[ByteBuffer] = Nil
  protected var currentBuf: ByteBuffer    = allocate(initSize)

  protected def allocate(size: Int): ByteBuffer

  final private def newBuffer(size: Int): Unit = {
    // flip current buffer (prepare for reading and set limit)
    currentBuf.flip()
    buffers = currentBuf :: buffers
    // replace current buffer with the new one, align to 16-byte border for small sizes
    currentBuf = allocate((math.max(size, expandSize) & ~15) + 16)
  }

  @inline final def alloc(size: Int): ByteBuffer = {
    if (currentBuf.remaining() < size)
      newBuffer(size)
    currentBuf
  }

  def asByteBuffer = {
    currentBuf.flip()
    if (buffers.isEmpty)
      currentBuf
    else {
      val bufList = (currentBuf :: buffers).reverse
      // create a new buffer and combine all buffers into it
      val comb = allocate(bufList.map(_.limit()).sum)
      bufList.foreach(buf => comb.put(buf))
      comb.flip()
      comb
    }
  }

  def asByteBuffers = {
    currentBuf.flip()
    (currentBuf :: buffers).reverse.toVector
  }
}

private[izumi] object ByteBufferProvider {
  final val initSize   = 512
  final val expandSize = initSize * 8
}

private[izumi] class HeapByteBufferProvider extends ByteBufferProvider {
  override protected def allocate(size: Int) = {
    if (pool.isDisabled)
      ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    else
      pool.allocate(size).getOrElse(ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN))
  }

  override def asByteBuffer = {
    currentBuf.flip()
    if (buffers.isEmpty)
      currentBuf
    else {
      // create a new buffer and combine all buffers into it
      val bufList = (currentBuf :: buffers).reverse
      val comb    = allocate(bufList.map(_.limit()).sum)
      bufList.foreach { buf =>
        // use fast array copy
        java.lang.System.arraycopy(buf.array, buf.arrayOffset, comb.array, comb.position(), buf.limit())
        comb.position(comb.position() + buf.limit())
        // release to the pool
        pool.release(buf)
      }
      comb.flip()
      comb
    }
  }
}

private[izumi] class DirectByteBufferProvider extends ByteBufferProvider {
  override protected def allocate(size: Int) = {
    if (pool.isDisabled)
      ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
    else
      pool.allocateDirect(size).getOrElse(ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN))
  }

  override def asByteBuffer = {
    currentBuf.flip()
    if (buffers.isEmpty)
      currentBuf
    else {
      // create a new buffer and combine all buffers into it
      val bufList = (currentBuf :: buffers).reverse
      val comb    = allocate(bufList.map(_.limit()).sum)
      bufList.foreach { buf =>
        comb.put(buf)
        // release to the pool
        pool.release(buf)
      }
      comb.flip()
      comb
    }
  }
}

private[izumi] trait DefaultByteBufferProviderFuncs {
  def provider: ByteBufferProvider
}
