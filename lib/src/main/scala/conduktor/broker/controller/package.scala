package conduktor.broker

import org.apache.kafka.common.PartitionInfo
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.durationInt
import zio.kafka.consumer.{Consumer, ConsumerSettings}
import zio.{Has, RManaged, ZIO, ZManaged}

//TODO turn this only into a service to provide consumer, url and connection settings should come from environment
package object controller {
  type Controller = Has[Controller.Service]

  object Controller {
    trait Service {
      def connect(
          urls:       List[String],
          properties: Map[String, String]
      ): ZIO[Clock with Blocking, Throwable, Map[String, List[PartitionInfo]]]
    }

    object Service {
      val live = new Service {
        override def connect(
            urls:       List[String],
            properties: Map[String, String]
        ): ZIO[Clock with Blocking, Throwable, Map[String, List[PartitionInfo]]] = {
          val settings: ConsumerSettings =
            ConsumerSettings(urls)
              .withClientId("client")
              .withGroupId(null)
              .withCloseTimeout(4.seconds)
          //TODO pass in rest of the properties

          val consumer: RManaged[Clock with Blocking, Consumer.Service] = Consumer.make(settings)
          //TODO should only provide a consumer
          consumer.use { consumer =>
            consumer.listTopics()
          }
        }

      }
    }
  }
}
