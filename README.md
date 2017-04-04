# teams-and-repositories
API for retrieving repositories belonging to teams.

For developers:
===============
 
 When you want you local mongo store to be refreshed with github data, do a HTTP GET on localhost:xxxx/api/cache/reload.
  This will get the data from github and persists it all into your local mongo instance configured via the mongodb.uri config key (ie. application.conf) 

 N.B Please make sure that mongod is running locally. You can run mongo easily in docker by running
 
    docker run -d --name teams-and-repositories-mongo mongo
 
 
 

