package org.apache.spark.streaming

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.spark.SparkConf
import org.apache.spark.serializer.SerializerManager
import org.apache.spark.streaming.util.WriteAheadLogUtils
import org.apache.spark.util.io.ChunkedByteBuffer
import org.apache.spark.util.{Clock, SystemClock}

import scala.reflect.ClassTag

/**
 * 2019-06-19 WilliamZhu(allwefantasy@gmail.com)
 */
class BinlogWriteAheadLog(
                           serverId: String,
                           serializerManager: SerializerManager,
                           conf: SparkConf,
                           hadoopConf: Configuration,
                           checkpointDir: String,
                           clock: Clock = new SystemClock) {
  private val writeAheadLog = WriteAheadLogUtils.createLogForReceiver(
    conf, new Path(checkpointDir, new Path("receivedData", serverId)).toString, hadoopConf)

  def write(items: Seq[RawEvent]) = {
    val ser = serializerManager.getSerializer(ClassTag(classOf[RawEvent]), true)
    val byteBuffer = ser.newInstance().serialize[Seq[RawEvent]](items)
    val serializedBlock = new ChunkedByteBuffer(byteBuffer.duplicate())
    writeAheadLog.write(serializedBlock.toByteBuffer, clock.getTimeMillis())
  }

  def read(f: (Seq[RawEvent]) => Unit) = {
    val ser = serializerManager.getSerializer(ClassTag(classOf[RawEvent]), true)
    val items = writeAheadLog.readAll()
    while (items.hasNext) {
      val item = ser.newInstance().deserialize[Seq[RawEvent]](items.next())
      f(item)
    }
  }

  def cleanupOldBlocks(threshTime: Long, waitForCompletion: Boolean = false) {
    writeAheadLog.clean(threshTime, waitForCompletion)
  }

  def stop() {
    writeAheadLog.close()
  }
}
