/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.config

import play.api.Configuration

import javax.inject.Inject
import com.google.common.io.BaseEncoding
import scala.concurrent.duration.FiniteDuration

class JenkinsConfig @Inject()(config: Configuration):
  object BuildJobs:
    val baseUrl: String = config.get[String]("jenkins.buildjobs.url")

    val authorizationHeader: String =
      val token   : String = config.get[String]("jenkins.buildjobs.token")
      val username: String = config.get[String]("jenkins.buildjobs.username")
      s"Basic ${BaseEncoding.base64().encode(s"$username:$token".getBytes("UTF-8"))}"

    val rebuilderAuthorizationHeader: String =
      val rebuilderUsername: String = config.get[String]("jenkins.buildjobs.rebuilder.username")
      val rebuilderToken   : String = config.get[String]("jenkins.buildjobs.rebuilder.token")
      s"Basic ${BaseEncoding.base64().encode(s"$rebuilderUsername:$rebuilderToken".getBytes("UTF-8"))}"

  object PerformanceJobs:
    val baseUrl : String            = config.get[String]("jenkins.performancejobs.url")
    val authorizationHeader: String =
      val token   : String = config.get[String]("jenkins.performancejobs.token")
      val username: String = config.get[String]("jenkins.performancejobs.username")
      s"Basic ${BaseEncoding.base64().encode(s"$username:$token".getBytes("UTF-8"))}"

  val queueThrottleDuration: FiniteDuration = config.get[FiniteDuration]("jenkins.queue.throttle")
  val buildThrottleDuration: FiniteDuration = config.get[FiniteDuration]("jenkins.build.throttle")
  val searchDepth          : Int            = config.get[Int]("cache.jenkins.searchDepth")
