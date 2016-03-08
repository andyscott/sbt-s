import com.typesafe.sbt.SbtScalariform

lazy val root = (project in file("."))
  .aggregate(core)

lazy val commonSettings = Seq(
  sbtPlugin := true,
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.0.0-M15" % "test"
  )
)

lazy val core = (project in file("core"))
  .settings(
    name            := "s-core"
  )
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "org.typelevel" %% "cats" % "0.4.1",
    "io.circe" %% "circe-core" % "0.3.0",
    "io.circe" %% "circe-generic" % "0.3.0",
    "io.circe" %% "circe-parser" % "0.3.0"
  ))
  .settings(
    libraryDependencies += compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies += "com.github.mpilquist" %% "simulacrum" % "0.7.0"
  )

lazy val `sbt-s`  = (project in file("plugin"))
  .settings(
    name            := "sbt-s"
  )
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "org.typelevel" %% "cats" % "0.4.1"
  ))
  .settings(
    libraryDependencies += compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies += "com.github.mpilquist" %% "simulacrum" % "0.7.0"
  )
  .settings(ScriptedPlugin.scriptedSettings: _*)
  .settings(
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  ).settings(
    scriptedDependencies <<= (publishLocal in core, scriptedDependencies) map { (_, _) => Unit }
  )
