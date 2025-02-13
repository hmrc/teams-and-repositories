# Teams and Repositories
API for retrieving information about repositories belonging to teams.

For developers:
===============

 When you want your local mongo store to be refreshed with github data, do an HTTP GET on localhost:xxxx/api/cache/reload.
  This will get the data from github and persists it all into your local mongo instance configured via the mongodb.uri config key (ie. application.conf)

 N.B Please make sure that mongod is running locally. You can run mongo easily in docker by running

    docker run -d --name teams-and-repositories-mongo mongo


The cache reload is disabled by default. Enable it by setting ```scheduler.teams.enabled=false```
