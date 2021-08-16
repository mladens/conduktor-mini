ThisBuild / organization := "conduktor"
ThisBuild / scalaVersion := "2.13.1"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / name         := "ConduktorMini"

lazy val javaFxDependencies = {
  // Determine OS version of JavaFX binaries
  lazy val osName = System.getProperty("os.name") match {
    case n if n.startsWith("Linux")   => "linux"
    case n if n.startsWith("Mac")     => "mac"
    case n if n.startsWith("Windows") => "win"
    case _                            => throw new Exception("Unknown platform!")
  }
  lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
  javaFXModules.map(m => "org.openjfx" % s"javafx-$m" % "16" classifier osName)
}

lazy val lib = (project in file("lib"))
  .settings(
    libraryDependencies := Seq(
      "dev.zio" %% "zio"       % "1.0.0",
      "dev.zio" %% "zio-kafka" % "0.15.0"
    )
  )

lazy val ui = (project in file("ui"))
  .settings(
    libraryDependencies := Seq(
      "dev.zio"     %% "zio"       % "1.0.0",
      "dev.zio"     %% "zio-kafka" % "0.15.0",
      "org.scalafx" %% "scalafx"   % "16.0.0-R24"
    ) ++ javaFxDependencies
  )
  .dependsOn(lib)
