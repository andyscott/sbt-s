addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.5.1")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

libraryDependencies <+= sbtVersion(v => "org.scala-sbt" % "scripted-plugin" % v)
