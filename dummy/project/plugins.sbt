lazy val build = (project in file("."))
  .dependsOn(ProjectRef(file("../../"), "sbt-s"))
