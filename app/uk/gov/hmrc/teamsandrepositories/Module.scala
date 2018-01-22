package uk.gov.hmrc.teamsandrepositories

import com.google.inject.AbstractModule

class Module() extends AbstractModule {

  override def configure(): Unit =
    bind(classOf[DataReloadScheduler]).asEagerSingleton()

}
