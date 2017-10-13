package uk.gov.hmrc.teamsandrepositories.errorhandlers

import com.google.inject.{Inject, Provider, Singleton}
import org.slf4j
import org.slf4j.LoggerFactory
import play.api.http.DefaultHttpErrorHandler
import play.api._
import play.api.mvc._
import play.api.routing.Router

import scala.concurrent.Future

@Singleton
class LoggingErrorHandler @Inject() (
                               env: Environment,
                               config: Configuration,
                               sourceMapper: OptionalSourceMapper,
                               router: Provider[Router]
                             ) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  override def onProdServerError(request: RequestHeader, exception: UsefulException): Future[Result] = {
    Logger.error("An server error has occurred.", exception)
    super.onProdServerError(request, exception)
  }

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Logger.error(s"A client error has occurred when trying to access ${request.domain} resulting in http code $statusCode")
    super.onClientError(request, statusCode, message)
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    Logger.error(s"A server error occurred when trying to access ${request.domain}", exception)
    super.onServerError(request, exception)
  }

  override protected def logServerError(request: RequestHeader, usefulException: UsefulException): Unit = {
    super.logServerError(request, usefulException)
  }
}