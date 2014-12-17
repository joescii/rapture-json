/**********************************************************************************************\
* Rapture JSON Library                                                                         *
* Version 1.0.8                                                                                *
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

case class JsonCastExtractor[T](ast: JsonAst, dataType: DataTypes.DataType)

trait Extractors extends internal.Extractors_1 {

  implicit def jsonExtractor(implicit ast: JsonAst): Extractor[Json, Json] =
    BasicExtractor({ x =>
      Json.construct(VCell(JsonDataType.jsonSerializer.serialize(x)), Vector())
    })

  implicit val stringExtractor: Extractor[String, Json] =
    BasicExtractor(x => x.$ast.getString(x.$root.value))

  implicit val doubleExtractor: Extractor[Double, Json] =
    BasicExtractor(x => x.$ast.getDouble(x.$root.value))

  implicit val intExtractor: Extractor[Int, Json] =
    BasicExtractor(x => x.$ast.getDouble(x.$root.value).toInt)

  implicit val booleanExtractor: Extractor[Boolean, Json] =
    BasicExtractor(x => x.$ast.getBoolean(x.$root.value))
  
  implicit val bigDecimalExtractor: Extractor[BigDecimal, Json] =
    BasicExtractor(x => x.$ast.getBigDecimal(x.$root.value))
  
  implicit val bigIntExtractor: Extractor[BigInt, Json] =
    BasicExtractor(x => x.$ast.getBigDecimal(x.$root.value).toBigInt)
}

package internal {

  trait Extractors_1 {
    implicit def jsonBufferExtractor[T](implicit jsonAst: JsonAst, ext: Extractor[T, Json]): Extractor[T, JsonBuffer] =
      new Extractor[T, JsonBuffer] {
        def construct(any: JsonBuffer, fail: Exception, ast: DataAst): T =
          ext.construct(Json.construct(VCell(any.$root.value), Vector()), fail, ast)
      }
  }
}
