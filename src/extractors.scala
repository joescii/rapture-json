/**********************************************************************************************\
* Rapture JSON Library                                                                         *
* Version 1.1.0                                                                                *
*                                                                                              *
* The primary distribution site is                                                             *
*                                                                                              *
*   http://rapture.io/                                                                         *
*                                                                                              *
* Copyright 2010-2014 Jon Pretty, Propensive Ltd.                                              *
*                                                                                              *
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file    *
* except in compliance with the License. You may obtain a copy of the License at               *
*                                                                                              *
*   http://www.apache.org/licenses/LICENSE-2.0                                                 *
*                                                                                              *
* Unless required by applicable law or agreed to in writing, software distributed under the    *
* License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    *
* either express or implied. See the License for the specific language governing permissions   *
* and limitations under the License.                                                           *
\**********************************************************************************************/

package rapture.json

import rapture.core._
import rapture.data._

import scala.reflect.macros._
import scala.annotation._

import language.experimental.macros
import language.higherKinds

package internal {

  case class JsonCastExtractor[T](ast: JsonAst, dataType: DataTypes.DataType)
  
  trait Extractors extends Extractors_1 {

    implicit def jsonExtractor(implicit ast: JsonAst): Extractor[Json, Json] =
      new Extractor[Json, Json] {
        def construct(any: Json, fail: Exception, dataAst: DataAst) =
          Json.construct(MutableCell(JsonDataType.jsonSerializer.serialize(any)), Vector())
      }
    
    implicit val stringExtractor: Extractor[String, Json] =
      new Extractor[String, Json] {
        def construct(any: Json, fail: Exception, ast: DataAst) =
          any.$ast.getString(any.$root.value)
      }

    implicit val doubleExtractor: Extractor[Double, Json] =
      new Extractor[Double, Json] {
        def construct(any: Json, fail: Exception, ast: DataAst) =
          any.$ast.getDouble(any.$root.value)
      }

    implicit val intExtractor: Extractor[Int, Json] =
      doubleExtractor.map(_.toInt)

    implicit val booleanExtractor: Extractor[Boolean, Json] =
      new Extractor[Boolean, Json] {
        def construct(any: Json, fail: Exception, ast: DataAst) =
          any.$ast.getBoolean(any.$root.value)
      }
    
    implicit val bigDecimalExtractor: Extractor[BigDecimal, Json] =
      new Extractor[BigDecimal, Json] {
        def construct(any: Json, fail: Exception, ast: DataAst) =
          any.$ast.getBigDecimal(any.$root.value)
      }
    
    implicit val bigIntExtractor: Extractor[BigInt, Json] =
      bigDecimalExtractor.map(_.toBigInt)
  }

  trait Extractors_1 {
    implicit def jsonBufferExtractor[T](implicit jsonAst: JsonAst, ext: Extractor[T, Json]):
        Extractor[T, JsonBuffer] =
      new Extractor[T, JsonBuffer] {
        def construct(any: JsonBuffer, fail: Exception, ast: DataAst): T =
          ext.construct(Json.construct(MutableCell(any.$root.value), Vector()), fail, ast)
      }
    
    implicit def jsonBufferToJsonExtractor(implicit ast: JsonBufferAst):
        Extractor[JsonBuffer, Json] =
      new Extractor[JsonBuffer, Json] {
        def construct(any: Json, fail: Exception, dataAst: DataAst) =
          JsonBuffer.construct(MutableCell(JsonDataType.jsonSerializer.serialize(any)),
              Vector())
      }

  }
}
