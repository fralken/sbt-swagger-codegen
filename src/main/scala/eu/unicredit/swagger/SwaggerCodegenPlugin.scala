/* Copyright 2015 UniCredit S.p.A.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
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
          throw new Exception(s"Unsupported swaggerModelFileSplitting option $any please choose one of (singleFile | oneFilePerSource | oneFilePerModel)")
      }
  }

  object autoImport {

     /*
      * Settings
      */
    val swaggerSourcesDir = settingKey[String]("swaggerSourcesDir")

    val swaggerClientCodeTargetDir = settingKey[String]("swaggerClientCodeTargetDir")

    val swaggerCodegenPackage = settingKey[String]("swaggerCodegenPackage")

    val swaggerModelFilesSplitting = settingKey[String]("swaggerModelFileSplitting")

    val swaggerGeneratePlayJsonRW = settingKey[Boolean]("swaggerGeneratePlayJsonRW")

    val swaggerCodeProvidedPackage = settingKey[String]("swaggerCodeProvidedPackage")

    val swaggerServerAsync = settingKey[Boolean]("swaggerServerAsync")

    /*
     * Tasks
     */
    val swaggerCleanTask = taskKey[Unit]("Clean swagger generated packages")

    val swaggerCodeGenTask = taskKey[Unit]("Generate swagger models and json converters")

    val swaggerPlayServerCodeGenTask = taskKey[Unit]("Generate swagger Play server routes and controllers boilerplate")

    val swaggerPlayClientCodeGenTask = taskKey[Unit]("Generate swagger Play client class with WS calls to specific routes")

  }

  final val swaggerCodegenPackageDefault = "swagger.codegen"
  final val swaggerSourcesDirDefault = "/src/main/swagger"
  final val swaggerClientCodeTargetDirDefault = "/src/main/swagger"
  final val swaggerModelFilesSplittingDefault = "singleFile"
  final val swaggerGeneratePlayJsonRWDefault = true
  final val swaggerCodeProvidedPackageDefault = "eu.unicredit"
  final val swaggerServerAsyncDefault = false

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
      "org.joda" % "joda-convert" % "1.7"),
    swaggerCleanTask := {
        val base = baseDirectory.value.getAbsolutePath
        val codegenPackage = swaggerCodegenPackage.?.value getOrElse swaggerCodegenPackageDefault

        swaggerCleanImpl(base, codegenPackage)
    },
    swaggerCodeGenTask := {
       val base = baseDirectory.value.getAbsolutePath
       val codegenPackage = swaggerCodegenPackage.?.value getOrElse swaggerCodegenPackageDefault
       val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
       val targetDir = swaggerClientCodeTargetDir.?.value getOrElse (base + swaggerClientCodeTargetDirDefault)
       val fileSplittingMode = swaggerModelFilesSplitting.?.value getOrElse swaggerModelFilesSplittingDefault
       val generatePlayJson = swaggerGeneratePlayJsonRW.?.value getOrElse swaggerGeneratePlayJsonRWDefault

       swaggerCodeGenImpl(base, codegenPackage, sourcesDir, fileSplittingMode, generatePlayJson, targetDir)
    },
    swaggerPlayServerCodeGenTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodegenPackage.?.value getOrElse swaggerCodegenPackageDefault
      val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
      val codeProvidedPackage = swaggerCodeProvidedPackage.?.value getOrElse swaggerCodeProvidedPackageDefault
      val async = swaggerServerAsync.?.value getOrElse swaggerServerAsyncDefault

      swaggerPlayServerCodeGenImpl(base, codegenPackage, sourcesDir, codeProvidedPackage, async)
    },
    swaggerPlayClientCodeGenTask := {
      val base = baseDirectory.value.getAbsolutePath
      val codegenPackage = swaggerCodegenPackage.?.value getOrElse swaggerCodegenPackageDefault
      val sourcesDir = swaggerSourcesDir.?.value getOrElse (base + swaggerSourcesDirDefault)
      val targetDir = swaggerClientCodeTargetDir.?.value getOrElse (base + swaggerClientCodeTargetDirDefault)

      swaggerPlayClientCodeGenImpl(base, codegenPackage, sourcesDir, targetDir)
    }
  )

  lazy val swaggerCleanCommand =
    Command.command("swaggerClean") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse swaggerCodegenPackageDefault

      swaggerCleanImpl(base, codegenPackage)

      state
    }

  def swaggerCleanImpl(base: String, codegenPackage: String) = {
    val destDir = FolderCreator.genPackage(base + "/src/main/scala", codegenPackage)

    def rm_r(f: java.io.File): Unit = {
        if (f.isDirectory())
          f.listFiles().foreach(rm_r(_))

        f.delete()
      }

    rm_r(destDir)
  }

  lazy val swaggerCodeGenCommand =
    Command.command("swaggerCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse swaggerCodegenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val targetDir: String =
        swaggerClientCodeTargetDir in currentRef get structure.data getOrElse (base + swaggerClientCodeTargetDirDefault)

      val fileSplittingMode: String =
        swaggerModelFilesSplitting in currentRef get structure.data getOrElse swaggerModelFilesSplittingDefault

      val generatePlayJson: Boolean =
        swaggerGeneratePlayJsonRW in currentRef get structure.data getOrElse swaggerGeneratePlayJsonRWDefault

      swaggerCodeGenImpl(base, codegenPackage, sourcesDir, fileSplittingMode, generatePlayJson, targetDir)

      state
    }

  def swaggerCodeGenImpl(base: String, codegenPackage: String, sourcesDir: String, fileSplittingMode: String, generatePlayJson: Boolean, targetDir: String) = {
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

      val destDir = FolderCreator.genPackage(targetDir, codegenPackage)

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
  }

  lazy val swaggerPlayServerCodeGenCommand =
    Command.command("swaggerPlayServerCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse swaggerCodegenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val codeProvidedPackage: String =
        swaggerCodeProvidedPackage in currentRef get structure.data getOrElse swaggerCodeProvidedPackageDefault

      val async: Boolean =
        swaggerServerAsync in currentRef get structure.data getOrElse swaggerServerAsyncDefault


      swaggerPlayServerCodeGenImpl(base, codegenPackage, sourcesDir, codeProvidedPackage, async)

      state
    }

    def swaggerPlayServerCodeGenImpl(base: String, codegenPackage: String, sourcesDir: String, codeProvidedPackage: String, async: Boolean) = {
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

      FileWriter.writeToFile(new File(base + "/src/main/resources/routes"), sr)

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

      val destDir = FolderCreator.genPackage(base + "/src/main/scala", codegenPackage + ".controller")

      controllers.foreach {
        case (fName, code) => FileWriter.writeToFile(new File(destDir, fName + ".scala"), code)
      }
    }


   lazy val swaggerPlayClientCodeGenCommand =
    Command.command("swaggerPlayClientCodeGen") { (state: State) =>
      val extracted: Extracted = Project.extract(state)
      import extracted._

      val base = currentProject.base.getAbsolutePath

      val codegenPackage: String =
        swaggerCodegenPackage in currentRef get structure.data getOrElse swaggerCodegenPackageDefault

      val sourcesDir: String =
        swaggerSourcesDir in currentRef get structure.data getOrElse (base + swaggerSourcesDirDefault)

      val targetDir: String =
        swaggerClientCodeTargetDir in currentRef get structure.data getOrElse (base + swaggerClientCodeTargetDirDefault)

      swaggerPlayClientCodeGenImpl(base, codegenPackage, sourcesDir, targetDir)

      state
    }

   def swaggerPlayClientCodeGenImpl(base: String, codegenPackage: String, sourcesDir: String, targetDir: String) = {
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

      val destDir = FolderCreator.genPackage(targetDir, codegenPackage + ".client")

      clients.foreach {
        case (fName, code) => FileWriter.writeToFile(new File(destDir, fName + ".scala"), code)
      }

   }
}
