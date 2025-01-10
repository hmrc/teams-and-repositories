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

package uk.gov.hmrc.teamsandrepositories.persistence

import org.mongodb.scala.ObservableFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.teamsandrepositories.model.OpenPullRequest

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class OpenPullRequestPersistenceSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with DefaultPlayMongoRepositorySupport[OpenPullRequest]:

    override protected val repository: OpenPullRequestPersistence = OpenPullRequestPersistence(mongoComponent)

    private val pr1 =
      OpenPullRequest(
          repoName = "example-pr2",
          title = "Some PR Title",
          url = "https://github.com/example-pr2/pull/1",
          author = "username1",
          createdAt = Instant.parse("2020-03-13T11:18:06Z")
        )

    private val pr2 =  
      OpenPullRequest(
        repoName = "example-repo3",
        title = "Some PR Title",
        url = "https://github.com/example-repo3/pull/1",
        author = "username2",
        createdAt = Instant.parse("2020-03-13T11:18:06Z")
      )

    "findOpenPullRequests" should :
      "get all open pull requests" in:
        repository.collection.insertMany(Seq(pr1, pr2)).toFuture().futureValue
        val results = repository.findOpenPullRequests().futureValue
        results shouldBe Seq(pr1, pr2)

      "get all open pull requests for a specific repository" in:
        repository.collection.insertMany(Seq(pr1, pr2)).toFuture().futureValue
        val results = repository.findOpenPullRequests(repoName = Some(pr1.repoName)).futureValue
        results shouldBe Seq(pr1)

      "get all open pull requests for a specific user" in:
        repository.collection.insertMany(Seq(pr1, pr2)).toFuture().futureValue
        val results = repository.findOpenPullRequests(author = Some(pr2.author)).futureValue
        results shouldBe Seq(pr2)

    "putOpenPullRequests" should:
      "insert all open pull requests" in:
        repository.putOpenPullRequests(Seq(pr1, pr2)).futureValue
        val results = repository.findOpenPullRequests().futureValue
        results shouldBe Seq(pr1, pr2)

    "putOpenPullRequest" should:
      "insert a single open pull request" in:
        repository.putOpenPullRequest(pr1).futureValue
        val results = repository.findOpenPullRequests().futureValue
        results shouldBe Seq(pr1)

    "deleteOpenPullRequest" should:
      "delete pr1" in:
        repository.collection.insertMany(Seq(pr1, pr2)).toFuture().futureValue
        repository.deleteOpenPullRequest(pr1.url).futureValue shouldBe 1
        repository.findOpenPullRequests().futureValue shouldBe Seq(pr2)

      "handle pr not found" in:
        repository.deleteOpenPullRequest(pr1.url).futureValue shouldBe 0
        repository.findOpenPullRequests().futureValue shouldBe Seq.empty
