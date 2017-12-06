addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")

addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.3.0")

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"
