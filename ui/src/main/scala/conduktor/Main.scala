package conduktor

import conduktor.ui.controller.SceneController
import javafx.application.Application
import javafx.stage.Stage
import scalafx.stage.{Stage => SStage}
import zio.{ExitCode, Has, RIO, URIO, ZIO, ZLayer}

import scala.util.chaining.scalaUtilChainingOps

class UI extends Application {
  override def start(stage: Stage): Unit = {
    val scalafxStage = new SStage(stage).tap(_.show())

    val rootAction:        RIO[Has[SStage], Unit]    = SceneController.showLoginScreen
    val rootActionWithEnv: ZIO[Any, Throwable, Unit] = rootAction.provideLayer(ZLayer.succeed(scalafxStage))

    zio.Runtime.default.unsafeRunAsync(rootActionWithEnv.exitCode)(_ => ())
  }
}

object Main extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    ZIO.succeed {
      Application.launch(classOf[UI])
    }.exitCode
  }
}
