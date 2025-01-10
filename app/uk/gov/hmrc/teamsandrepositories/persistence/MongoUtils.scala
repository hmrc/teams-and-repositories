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

package uk.gov.hmrc.teamsandrepositories.persistence

import org.bson.conversions.Bson
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.{Collation, DeleteManyModel, DeleteOptions, Filters, ReplaceOneModel, ReplaceOptions, WriteModel}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


object MongoUtils:
  /**
    * Equivalent to ```
    *   withSessionAndTransacion { s =>
    *     for {
    *       _ <- collection.deleteMany(s, oldValsFilter).toFuture()
    *       _ <- collection.insertMany(s, newVals).toFuture()
    *     } yield ()
    *   }
    * ```
    * but is more efficient on mongo since it avoids a transaction and avoids making changes
    * when the data hasn't changed.
    *
    * @param collection
    * @param newVals the new vals to insert
    * @param oldValsFilter describes the domain to be replaced
    * @param compareById describes uniqueness to decide if a value needs removing
    * @param filterById describes uniqueness as a Bson filter
    * @param ec
    * @return
    */
  def replace[A : ClassTag](
    collection   : MongoCollection[A],
    newVals      : Seq[A],
    oldValsFilter: Bson              = Filters.empty(),
    compareById  : (A, A) => Boolean,
    filterById   : A => Bson,
    collation    : Collation         = Collations.default
  )(using ExecutionContext): Future[(Int, Int)] =
    def bulkWrite(updates: Seq[WriteModel[_ <: A]]): Future[Int] =
      if updates.isEmpty then
        Future.successful(0)
      else
        collection.bulkWrite(updates).toFuture().map(res => res.getModifiedCount)

    for
      old      <- collection.find(oldValsFilter).toFuture()
      upserted <- //upsert any that were not present already
                  bulkWrite:
                    newVals
                      .filterNot(old.contains)
                      .map: entry =>
                        ReplaceOneModel(
                          filterById(entry),
                          entry,
                          ReplaceOptions().collation(collation).upsert(true)
                        )
      deleted  <- // delete any that are no longer present
                  bulkWrite:
                    old
                      .filterNot(oldC => newVals.exists(newC => compareById(oldC, newC)))
                      .map: entry =>
                        DeleteManyModel(
                          filterById(entry),
                          DeleteOptions().collation(collation)
                        )
    yield (upserted, deleted)
