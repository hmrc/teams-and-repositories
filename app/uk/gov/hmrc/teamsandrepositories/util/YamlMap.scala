/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.teamsandrepositories.util

import org.yaml.snakeyaml.Yaml
import play.api.Logging

import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.control.NonFatal

case class YamlMap(asMap: Map[String, Object]) extends Logging:

  def get[A](key: String): Option[A] =
    asMap.get(key).map(_.asInstanceOf[A])
    
  def getAsOpt[A <: Object : ClassTag](key: String): Option[A] =
    asMap.get(key).flatMap:
      case t: A => Some(t)
      case _    => None

  def getArray(key: String): Option[List[String]] =
    try
      get[java.util.List[String]](key).map(_.asScala.toList)
    catch
      case NonFatal(ex) =>
        logger.warn(s"Unable to get '$key' from yaml, problems were: ${ex.getMessage}")
        None

object YamlMap:
  def parse(contents: String): Try[YamlMap] =
    Try(Yaml().load[java.util.Map[String, Object]](contents))
      .map(_.asScala.toMap)
      .map(apply)
