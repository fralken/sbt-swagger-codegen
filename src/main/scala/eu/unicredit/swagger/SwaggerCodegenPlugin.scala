package eu.unicredit.swagger

import sbt._
import Keys._

import scala.collection.JavaConversions._

object SwaggerCodegenPlugin extends AutoPlugin {

  object FileSplittingModes {
    case object SingleFile
    case object OneFilePerSource
    case object OneFilePerModel

    def apply(s: String) =
      s match {
        case "singleFile"       => SingleFile
        case "oneFilePerSource" => OneFilePerSource
        case "oneFilePerModel"  => OneFilePerModel
        case any =>
          throw new Exception(s"Unsupported swaggerModelFileSplitting option $any plese choose one of (singleFile | oneFilePerSource | oneFilePerModel)")
      }
  }

  object autoImport {

    val swaggerSourcesDir = settingKey[String]("swaggerSourcesDir")

    val swaggerCodegenPackage = settingKey[String]("swaggerCodegenPackage")

    val swaggerModelFilesSplitting = settingKey[String]("swaggerModelFileSplitting")

    val swaggerGeneratePlayJsonRW = settingKey[Boolean]("swaggerGeneratePlayJsonRW")

    val swaggerCodeProvidedPackage = settingKey[String]("swaggerCodeProvidedPackage")

    val swaggerServerAsync = settingKey[Boolean]("swaggerServerAsync")
    
  }

  import autoImport._
  override def trigger = allRequirements
  override lazy val buildSettings = Seq(
    commands ++= Seq(
      swaggerCleanCommand,
      swaggerCodeGenCommand,
      swaggerPlayServerCodeGenCommand,
      swaggerPlayClientCodeGenCommand))

  override val projectSettings = Seq(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % "2.4.0", //always imported???
      "joda-time" % "joda-time" % "2.7",
      "org.joda" % "joda-convert" % "1.7"))

  lazy val swaggerCleanCommand =
    Command.command("swaggerClean") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse "swagger.codegen"

      val destDir = FolderCreator.genPackage(currentProject.base + "/src/main/scala", codegenPackage)

      def rm_r(f: java.io.File): Unit = {
        if (f.isDirectory())
          f.listFiles().foreach(rm_r(_))

        f.delete()
      }

      rm_r(destDir)

      state
    }

  lazy val swaggerCodeGenCommand =
    Command.command("swaggerCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse currentProject.base + "/src/main/swagger"

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse "swagger.codegen"

      val fileSplittingMode: String =
        swaggerModelFilesSplitting in currentRef get structure.data getOrElse "singleFile"

      val generatePlayJson: Boolean =
        swaggerGeneratePlayJsonRW in currentRef get structure.data getOrElse true

      val sDir = new File(sourcesDir)

      val models: Map[String, Map[String, String]] =
        if (sDir.exists() && sDir.isDirectory()) {
          (for {
            file <- sDir.listFiles()
            fName = file.getName
            fPath = file.getAbsolutePath
            if (fName.endsWith(".json") || fName.endsWith(".yaml"))
          } yield {
            fName -> CodeGen.generateModels(fPath)
          }).toMap
        } else Map()

      val jsonFormats =
        CodeGen.generateJsonRW(
          if (sDir.exists() && sDir.isDirectory()) {
            (for {
              file <- sDir.listFiles()
              fName = file.getName
              fPath = file.getAbsolutePath
              if (fName.endsWith(".json") || fName.endsWith(".yaml"))
            } yield {
              fPath
            }).toList
          } else List())

      val destDir = FolderCreator.genPackage(currentProject.base + "/src/main/scala", codegenPackage)

      def getFileName(s: String) =
        new String(s.toCharArray().toList.takeWhile(_ == '.').toArray)

      import FileSplittingModes._
      FileSplittingModes(fileSplittingMode) match {
        case SingleFile =>
          val code =
            CodeGen.generateModelInit(codegenPackage) +
              models.values.map(_.values).flatten.toList.distinct.mkString("\n", "\n\n", "\n")

          FileWriter.writeToFile(new File(destDir, "Model.scala"), code)
        case OneFilePerSource =>
          (models.map {
            case (k, m) =>
              k ->
                (CodeGen.generateModelInit(codegenPackage) +
                  m.values.toList.distinct.mkString("\n", "\n\n", "\n"))
          }).foreach {
            case (k, code) =>
              FileWriter.writeToFile(new File(destDir, s"${getFileName(k)}.scala"), code)
          }
        case OneFilePerModel =>
          models.values.flatten.foreach {
            case (k, v) =>
              val code =
                CodeGen.generateModelInit(codegenPackage) + "\n" + v
              FileWriter.writeToFile(new File(destDir, s"${getFileName(k)}.scala"), code)
          }
      }

      if (generatePlayJson) {
        val jsonDir = FolderCreator.genPackage(destDir.getAbsolutePath, "json")
        val code =
          CodeGen.generateJsonInit(codegenPackage) + "\n\n" +
            CodeGen.generateJsonImplicits(jsonFormats.values.toList) //.mkString("\n","\n\n","\n")

        FileWriter.writeToFile(new File(jsonDir, s"package.scala"), code)
      }

      state
    }

  lazy val swaggerPlayServerCodeGenCommand =
    Command.command("swaggerPlayServerCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse currentProject.base + "/src/main/swagger"

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse "swagger.codegen"

      val codeProvidedPackage: String =
        swaggerCodeProvidedPackage in currentRef get structure.data getOrElse "eu.unicredit"
        
      val async: Boolean =
        swaggerServerAsync in currentRef get structure.data getOrElse false
        

      val sDir = new File(sourcesDir)

      val routes: Map[String, Seq[String]] =
        if (sDir.exists() && sDir.isDirectory()) {
          (for {
            file <- sDir.listFiles()
            fName = file.getName
            fPath = file.getAbsolutePath
            if (fName.endsWith(".json") || fName.endsWith(".yaml"))
          } yield {
            fPath -> CodeGen.generatePlayServerRoutes(fPath, codegenPackage)
          }).toMap
        } else Map()

      val sr =
        routes.values.flatten.toList.distinct.mkString("\n", "\n\n", "\n")

      FileWriter.writeToFile(new File(currentProject.base + "/src/main/resources/routes"), sr)

      val controllers: Map[String, String] =
        if (sDir.exists() && sDir.isDirectory()) {
          (for {
            file <- sDir.listFiles()
            fName = file.getName
            fPath = file.getAbsolutePath
            if (fName.endsWith(".json") || fName.endsWith(".yaml"))
          } yield {
            CodeGen.generatePlayServerStub(fPath, codegenPackage, codeProvidedPackage, async)
          }).toMap
        } else Map()

      val destDir = FolderCreator.genPackage(currentProject.base + "/src/main/scala", codegenPackage + ".controller")

      controllers.foreach {
        case (fName, code) => FileWriter.writeToFile(new File(destDir, fName + ".scala"), code)
      }

      state
    }

   lazy val swaggerPlayClientCodeGenCommand =
    Command.command("swaggerPlayClientCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse currentProject.base + "/src/main/swagger"

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse "swagger.codegen"

      val sDir = new File(sourcesDir)
      
      val clients: Map[String, String] =
        if (sDir.exists() && sDir.isDirectory()) {
          (for {
            file <- sDir.listFiles()
            fName = file.getName
            fPath = file.getAbsolutePath
            if (fName.endsWith(".json") || fName.endsWith(".yaml"))
          } yield {
            CodeGen.generatePlayClientStub(fPath, codegenPackage)
          }).toMap
        } else Map()

      val destDir = FolderCreator.genPackage(currentProject.base + "/src/main/scala", codegenPackage + ".client")

      clients.foreach {
        case (fName, code) => FileWriter.writeToFile(new File(destDir, fName + ".scala"), code)
      }

      state
    }
}