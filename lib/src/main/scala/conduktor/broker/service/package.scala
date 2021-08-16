package conduktor.broker

import conduktor.broker.controller.Controller
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, Subscription}
import zio.kafka.serde.Deserializer
import zio.stream.ZStream
import zio.{Has, ZIO}

package object service {
  type KafkaService = Has[KafkaService.Service]
  object KafkaService {
    case class TopicInfo(name:        String, partitions: Int)
    case class PartitionDetails(name: String, isr:        Int, partition: Int, leader: String)
    case class TopicRecord(offset:    Long, key:          String, value: String, timestamp: Long, partition: Long)

    sealed trait ServiceError
    case object IllegalUrl extends ServiceError
    case object ConsumerError extends ServiceError

    trait Service {
      def getTopics(
          urls:       List[String],
          properties: Map[String, String]
      ): ZIO[Clock with Blocking with Controller, ServiceError, List[TopicInfo]]
      def consumeTopic(topic: String, offset: String): ZStream[Consumer, Throwable, TopicRecord]
    }
    object Service {
      val live = new Service {
        override def getTopics(
            urls:       List[String],
            properties: Map[String, String]
        ): ZIO[Clock with Blocking with Controller, ServiceError, List[TopicInfo]] = {
          (for {
            controller <- ZIO
              .environment[Controller] //Might aswell obtain the consumer right here, requiring consumer settings from env
            topicPartitions <- controller.get.connect(urls, properties)
          } yield {
            topicPartitions.map {
              case (topic, partitions) =>
                TopicInfo(topic, partitions.size)
            }.toList
          }).mapError(_ => IllegalUrl)
        }

        override def consumeTopic(topic: String, offset: String): ZStream[Consumer, Throwable, TopicRecord] = {
          /*TODO
           *  use consumer directly with subscribe and Subscription.manual, manually setting all topic partitions
           *  in order to avoid group management
           *  Seek all partitions to provided offsets
           *  move it to Controller.Service and wrap into a ZStream without using `plainStream`
           * */
          Consumer
            .subscribeAnd(Subscription.topics(topic))
            .plainStream(
              keyDeserializer   = Deserializer.string,
              valueDeserializer = Deserializer.string,
              outputBuffer      = 1
            )
            .map { record =>
              TopicRecord(record.offset.offset, record.key, record.value, record.timestamp, record.partition)
            }
        }

      }
    }
  }
}
