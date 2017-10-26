package uk.gov.hmrc.teamsandrepositories

import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter
import uk.gov.hmrc.play.bootstrap.filters.MicroserviceFilters

class FiltersWithCORS @Inject()(defaultFilters : MicroserviceFilters, corsFilter: CORSFilter)
  extends DefaultHttpFilters(defaultFilters.filters :+ corsFilter: _*)