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
package eu.unicredit.swagger.generators

import sbt._
import eu.unicredit.swagger.{SwaggerConversion, StringUtils}

import treehugger.forest._
import definitions._
import treehuggerDSL._

import io.swagger.parser.SwaggerParser
import io.swagger.models.properties._
import io.swagger.models._
import io.swagger.models.parameters._
import scala.collection.JavaConversions._

object DefaultJsonGenerator {
  def dependencies: Seq[sbt.ModuleID] = Seq(
    "com.typesafe.play" %% "play-json" % DefaultPlay.version
  )
}

class DefaultJsonGenerator extends JsonGenerator with SwaggerConversion {

  def generateJsonInit(packageName: String): String = {
    val initTree =
      BLOCK {
        Seq(
          IMPORT("play.api.libs.json", "_"),
          IMPORT("play.api.libs.functional.syntax", "_"))
      } inPackage packageName

    treeToString(initTree)
  }

  def generateJsonImplicits(vds: List[ValDef]): String = {
    val tree =
      PACKAGEOBJECTDEF("json") := BLOCK(vds)

    treeToString(tree)
  }

  def generateJsonRW(fileName: String): List[ValDef] = {
    val swagger = new SwaggerParser().read(fileName)

    val models = swagger.getDefinitions

    (for {
      (name, model) <- models
      (c, m) <- Seq(("Reads", "read"), ("Writes", "write"))
    } yield {

      val vd =
        VAL(s"$name$c", s"$c[$name]") withFlags (Flags.IMPLICIT, Flags.LAZY) := ({
        def mtd(prop: Property) = if (prop.getRequired) "as" else "asOpt"

        c match {
          case "Reads" =>
            NEW(ANONDEF(s"$c[$name]") := BLOCK(
              DEF(s"${m}s", s"JsResult[$name]") withFlags Flags.OVERRIDE withParams PARAM("json", "JsValue") := REF("JsSuccess") APPLY (REF(name) APPLY (
                for ((pname, prop) <- model.getProperties) yield PAREN(REF("json") INFIX ("\\", LIT(pname))) DOT mtd(prop) APPLYTYPE propType(prop, false)))))
          case "Writes" =>
            NEW(ANONDEF(s"$c[$name]") := BLOCK(
              DEF(s"${m}s", "JsValue") withFlags Flags.OVERRIDE withParams PARAM("o", name) := REF("JsObject") APPLY (SeqClass APPLY (
                for ((pname, prop) <- model.getProperties) yield LIT(pname) INFIX ("->", (REF("Json") DOT "toJson")(REF("o") DOT pname))) DOT "filter" APPLY (REF("_") DOT "_2" INFIX ("!=", REF("JsNull"))))))
        }})

      vd
    }).toList
  }

  def generateJson(destPackage: String, vds: List[ValDef]): Iterable[SyntaxString] = {
    val pre = generateJsonInit(destPackage)

    val tree =
        PACKAGEOBJECTDEF("json") := BLOCK(vds)

    val code = treeToString(tree)
    Seq(SyntaxString("json", pre, code))
  }

  def generate(fileName: String, destPackage: String): Iterable[SyntaxString] = {
    generateJson(destPackage, generateJsonRW(fileName))
  }
}
