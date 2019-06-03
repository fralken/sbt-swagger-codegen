package eu.unicredit.swagger.dependencies

object PlayWsStandalone {
  val version = "1.1.3"
}

object DefaultModelGenerator {
  def dependencies: Seq[(Language.Value, String, String, String)] = Seq()
}

object DefaultJsonGenerator {
  def dependencies: Seq[(Language.Value, String, String, String)] = Seq(
    (Language.Scala, "com.typesafe.play", "play-ws-standalone-json", PlayWsStandalone.version)
  )
}

object DefaultServerGenerator {
  def dependencies: Seq[(Language.Value, String, String, String)] = Seq()
}

object DefaultClientGenerator {
  def dependencies: Seq[(Language.Value, String, String, String)] = Seq(
    (Language.Scala, "com.typesafe.play", "play-ahc-ws-standalone", PlayWsStandalone.version)
  )
}
