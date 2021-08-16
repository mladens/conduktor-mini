package conduktor.ui.controller

import conduktor.broker.controller.Controller
import conduktor.broker.service.KafkaService
import conduktor.broker.service.KafkaService.TopicInfo
import conduktor.ui.controller.SceneController.ClusterSettings
import conduktor.ui.graph.SceneGraph
import conduktor.ui.graph.SceneGraph.ConsumerView
import conduktor.ui.graph.SceneGraph.ConsumerView.MessageItem
import scalafx.application.Platform
import scalafx.scene.Scene
import scalafx.stage.Stage
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, ConsumerSettings}
import zio.{Has, RIO, ZIO, ZLayer}

import scala.concurrent.Future
import scala.util.Random

object SceneController {
  case class ClusterSettings(bootstrap: List[String], properties: Map[String, String])

  def showLoginScreen: RIO[Has[Stage], Unit] = {
    ZIO.environment[Has[Stage]].flatMap { stage =>
      ZIO.succeed {
        val actions = new ControllerActions(stage.get) //TODO pull this out of env aswell, should be layer that depends on stage
        Platform.runLater {
          stage.get[Stage].setScene(SceneGraph.Login.scene(actions.loginActions))
        }
      }
    }
  }

  def showTopicList(topicList: Seq[TopicInfo]): RIO[Has[Stage] with Has[ClusterSettings], Unit] = {
    ZIO.environment[Has[Stage] with Has[ClusterSettings]].flatMap { env =>
      ZIO.succeed {
        val actions = new ControllerActions(env.get[Stage])
          .topicActions(env.get[ClusterSettings]) //TODO pull this out of env aswell, should be layer that depends on stage
        Platform.runLater {
          env.get[Stage].setScene(SceneGraph.TopicList.scene(topicList.toList, actions))
        }
      }
    }
  }

  def showConsumerScreen(topic: TopicInfo): RIO[Has[Stage] with Has[ClusterSettings], Unit] = {
    ZIO.environment[Has[Stage] with Has[ClusterSettings]].flatMap { env =>
      ZIO.succeed {
        val actions = new ControllerActions(env.get[Stage])
          .consumerActions(env.get[ClusterSettings]) //TODO pull this out of env aswell, should be layer that depends on stage
        Platform.runLater {
          val stage = new Stage()
          stage.setScene(new Scene(SceneGraph.ConsumerView.scene(actions)(topic)))
          stage.show()
        }
      }
    }
  }

  def showMessageDetails(msg: MessageItem): RIO[Has[Stage] with Has[ClusterSettings], Unit] = {
    ZIO.environment[Has[Stage]].flatMap { stage =>
      ZIO.succeed {
        Platform.runLater {
          val stage = new Stage()
          stage.setScene(new Scene(SceneGraph.MessageView.scene(msg)))
          stage.show()
          ()
        }
      }
    }
  }
}

/*Controller static methods reference actions (need to provide them to build views)
  Actions need a Unit return type since they are invoked from view, and asynchronous
  They should only have a simple parameter provided by view, any dependencies need to be provided via env/context on "wire" time
 */
class ControllerActions(stage: Stage) {
  val loginActions = new SceneGraph.Login.Actions {
    override def connect(urls: List[String], properties: Map[String, String] = Map.empty): Unit = {

      val clusterSettings = ClusterSettings(urls, properties)
      val deps            = Clock.live ++ Blocking.live ++ ZLayer.succeed(clusterSettings) ++ ZLayer.succeed(stage)

      val getTopics: ZIO[Any, Any, Unit] = //TODO Task[Unit]
        KafkaService.Service.live
          .getTopics(urls, properties)
          .flatMap { topics =>
            SceneController.showTopicList(topics)
          }
          .provideSomeLayer[Controller](deps)
          .provideLayer(ZLayer.succeed(Controller.Service.live)) //TODO remove dependency on Controller.Service, get it from KafkaService

      zio.Runtime.default.unsafeRunAsync(getTopics)(_ => ()) //TODO log exit as action result
    }
  }

  def topicActions(clusterSettings: ClusterSettings) = new SceneGraph.TopicList.Actions {
    override def consume(topic: TopicInfo): Unit = {
      val action: RIO[Has[Stage] with Has[ClusterSettings], Unit] = SceneController.showConsumerScreen(topic)
      val deps = ZLayer.succeed(clusterSettings) ++ ZLayer.succeed(stage)

      zio.Runtime.default.unsafeRunAsync(
        action.provideLayer(deps)
      )(_ => ())
    }
  }

  def consumerActions(clusterSettings: ClusterSettings) = new ConsumerView.Actions {
    override def start(
        topic:      String,
        offset:     String,
        recordSink: MessageItem => Unit,
        cancel:     Future[Unit]
    ): Unit = {
      val consumption: ZIO[Consumer, Throwable, Unit] = KafkaService.Service.live.consumeTopic(topic, offset).foreach {
        record =>
          ZIO.succeed {
            val messageItem = MessageItem(record.key, record.value, record.timestamp, record.offset, record.partition)
            Platform
              .runLater(
                recordSink(messageItem)
              )
          }
      }
      val cancelation = ZIO.fromFuture(_ => cancel)
      val both        = consumption race cancelation

      val settings: ConsumerSettings =
        ConsumerSettings(clusterSettings.bootstrap)
          .withGroupId("conduktor-mini")
          .withProperty("enable.auto.commit", "false") //TODO properties
          .withProperty("auto.offset.reset", "earliest")
      //TODO properties

      val run: ZIO[Clock with Blocking, Throwable, Unit] =
        both.provideLayer(Consumer.make(settings).toLayer)

      val running: ZIO[Any, Throwable, Unit] = run.provideLayer(Clock.live ++ Blocking.live)
      zio.Runtime.default.unsafeRunAsync(running)(A => println(A)) //TODO Error handing on failure
    }

    override def select(msg: MessageItem): Unit = {
      val action: RIO[Has[Stage] with Has[ClusterSettings], Unit] = SceneController.showMessageDetails(msg)
      val deps = ZLayer.succeed(clusterSettings) ++ ZLayer.succeed(stage)
      action.provideLayer(deps)
      zio.Runtime.default.unsafeRunAsync(
        action.provideLayer(deps)
      )(_ => ())
    }
  }

}
