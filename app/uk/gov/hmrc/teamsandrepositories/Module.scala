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

package uk.gov.hmrc.teamsandrepositories

import play.api.{Configuration, Environment, Logger}
import play.api.inject.Binding
import uk.gov.hmrc.teamsandrepositories.schedulers.*
import uk.gov.hmrc.teamsandrepositories.notification.MdtpEventHandler

class Module() extends play.api.inject.Module:

  private val logger = Logger(getClass)

  private def conditionalBindings(configuration: Configuration): Seq[Binding[?]] =
    if configuration.get[Boolean]("aws.sqs.enabled") then
      Seq(
        bind[MdtpEventHandler].toSelf.eagerly()
      )
    else
      logger.warn("SQS handlers are disabled")
      Seq.empty

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] =
    Seq(
      bind[DataReloadScheduler            ].toSelf.eagerly()
    , bind[OpenPrDataReloadScheduler      ].toSelf.eagerly()
    , bind[GithubRatelimitMetricsScheduler].toSelf.eagerly()
    , bind[JenkinsRebuildScheduler        ].toSelf.eagerly()
    , bind[JenkinsReloadScheduler         ].toSelf.eagerly()
    , bind[BranchProtectionRulesScheduler ].toSelf.eagerly()
    ) ++
    conditionalBindings(configuration)
