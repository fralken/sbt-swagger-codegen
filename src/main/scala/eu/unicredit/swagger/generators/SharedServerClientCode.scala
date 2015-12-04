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

trait SharedServerClientCode extends StringUtils with SwaggerConversion {
  import java.io.File.separator
  import java.io.File.separatorChar

  def objectNameFromFileName(fn: String, obj: String) = {
    val sep = if (separatorChar == 92.toChar) "\\\\" else separator
    fn.split(sep).toList.last.replace(".yaml", "").replace(".json", "").capitalize + obj
  }

  def genMethodCall(className: String, methodName: String, params: Seq[Parameter]): String = {
    val p = getMethodParamas(params).map { case (n, v) => s"$n: ${treeToString(v.tpt)}" }
    // since it is a route definition, this is not Scala code, so we generate it manually
    s"$className.$methodName" + p.mkString("(", ", ", ")")
  }

  def getMethodParamas(params: Seq[Parameter]): Map[String, ValDef] = {
    params.filter {
      case path: PathParameter => true
      case query: QueryParameter => true
      case body: BodyParameter => false
      case _ => println("unmanaged parameter please contact the developer to implement it XD"); false
    }.sortWith((p1, p2) => //the order musty be verified...
      p1 match {
        case _: PathParameter =>
          p2 match {
            case _: PathParameter => true
            case _: QueryParameter => true
            case _ => true
          }
        case _: QueryParameter =>
          p2 match {
            case _: PathParameter => false
            case _: QueryParameter => true
            case _ => true
          }
        case _ => true
      }).map(p => {
      (p.getName,
        (p match {
          case pp: PathParameter =>
            PARAM(pp.getName, paramType(pp))
          case qp: QueryParameter =>
            if (qp.getDefaultValue == null)
              PARAM(qp.getName, paramType(qp))
            else
              PARAM(qp.getName, paramType(qp))
        }): ValDef)
    }).toMap
  }

}
