package conduktor.ui.graph

import conduktor.broker.service.KafkaService.TopicInfo
import conduktor.ui.graph.SceneGraph.ConsumerView.MessageItem
import javafx.collections.FXCollections
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout.{GridPane, HBox, VBox}
import scalafx.scene.text.Text

import java.time.Instant
import scala.concurrent.{Future, Promise}
import scala.util.chaining.scalaUtilChainingOps

object SceneGraph {
  object Login {
    trait Actions {
      def connect(urls: List[String], properties: Map[String, String] = Map.empty): Unit
    }

    private def parseProperties(properties: String): Map[String, String] =
      properties
        .split("\n")
        .map(_.split("=").take(2))
        .collect {
          case Array(key, value) => key -> value
        }
        .toMap

    private def parseContactPoints(contacts: String): List[String] =
      contacts.split(",").toList

    def scene(actions: Actions) = {
      val hostInput      = new TextField
      val propertiesArea = new TextArea

      val connectButton = new Button("connect").tap {
        _.setOnAction { _ =>
          actions.connect(
            parseContactPoints(hostInput.getText),
            parseProperties(propertiesArea.getText)
          )
        }
      }

      val rootNode = new GridPane()
      rootNode.setHgap(20)
      rootNode.setVgap(3)
      rootNode.add(new Label("Bootstrap servers:"), 0, 0)
      rootNode.add(hostInput, 1, 0)
      rootNode.add(new Label("Properties:"), 0, 1)
      rootNode.add(propertiesArea, 1, 1)
      rootNode.add(connectButton, 1, 2)

      new Scene(rootNode)
    }
  }
  object TopicList {
    trait Actions {
      def consume(topic: TopicInfo): Unit
    }

    def scene(topics: List[TopicInfo], actions: Actions) = {
      val rootNode = new GridPane()
      rootNode.setHgap(20)
      rootNode.setVgap(3)
      rootNode.add(new Label("Topic"), 0, 0)
      rootNode.add(new Label("Partitions"), 1, 0)
      rootNode.add(new Separator(), 0, 1)
      rootNode.add(new Separator(), 1, 1)
      topics.zipWithIndex.foreach {
        case (topic, index) =>
          val hLink = new Hyperlink(topic.name).tap {
            _.setOnMouseClicked { _ =>
              actions.consume(topic)
            }
          }
          val partitionsCount = new Text(topic.partitions.toString)
          rootNode.add(hLink, 0, index + 2)
          rootNode.add(partitionsCount, 1, index + 2)
      }

      new Scene(rootNode)
    }
  }
  object ConsumerView {
    case class MessageItem(key: String, value: String, timestamp: Long, offset: Long, partition: Long) {
      override def toString: String = s"Time: ${Instant.ofEpochMilli(timestamp)}   Key: ${key}"
    }

    trait Actions {
      def start(
          topic:      String,
          offset:     String,
          recordSink: MessageItem => Unit,
          cancel:     Future[Unit]
      ): Unit
      def select(msg: MessageItem): Unit
    }

    def scene(actions: Actions)(topic: TopicInfo) = {

      val topicLabel = {
        new Label(s"Topic: ${topic.name}")
      }

      val offsetSelector = new ChoiceBox[String]().tap { cb =>
        cb.getItems.add("earliest")
        cb.getItems.add("latest")
        cb.delegate.setValue("earliest")
      }

      val recordsPanel = new ListView[MessageItem]()
      val recordsList  = FXCollections.observableArrayList[MessageItem]()
      recordsPanel.setItems(recordsList)
      val recordUpdate: MessageItem => Unit = msg => recordsList.add(msg)
      recordsPanel.setOnMouseClicked(_ => actions.select(recordsPanel.getSelectionModel.getSelectedItem))

      recordsPanel.setPrefWidth(600)

      val stopButton  = new Button("Stop")
      val startButton = new Button("Start")

      stopButton.setDisable(true)

      startButton.setOnAction { _ =>
        stopButton.setDisable(false)
        startButton.setDisable(true)
        recordsList.clear()

        val offset = offsetSelector.getValue
        val stop   = Promise[Unit]()

        stopButton.setOnAction { _ =>
          startButton.setDisable(false)
          stopButton.setDisable(true)
          stop.success(())
        }
        actions.start(topic.name, offset, recordUpdate, stop.future)
      }

      def leftPane = new VBox {
        children = Seq(
          topicLabel,
          offsetSelector,
          new HBox {
            children = Seq(startButton, stopButton)
          }
        )
      }

      def rightPane: VBox = new VBox {
        children = Seq(
          recordsPanel
        )
      }

      new Scene {
        content = new HBox {
          children = Seq(
            leftPane,
            rightPane
          )
        }
      }
    }
  }
  object MessageView {
    def scene(msg: MessageItem) = {
      val rootNode = new GridPane()
      rootNode.setHgap(20)
      rootNode.setVgap(3)
      rootNode.add(new Label("Timestamp:"), 0, 0)
      rootNode.add(new Text(Instant.ofEpochMilli(msg.timestamp).toString), 1, 0)
      rootNode.add(new Label("Key:"), 0, 1)
      rootNode.add(new Text(msg.key), 1, 1)
      rootNode.add(new Label("Value:"), 0, 2)
      rootNode.add(new Text(msg.value), 1, 2)
      rootNode.add(new Label("Partition:"), 0, 3)
      rootNode.add(new Text(msg.partition.toString), 1, 3)
      rootNode.add(new Label("Offset:"), 0, 4)
      rootNode.add(new Text(msg.offset.toString), 1, 4)
      new Scene(rootNode)
    }
  }
}
