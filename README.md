# teams-and-repositories
API for retrieving repositories belonging to teams.

For developers:
===============
 
 When you want you local mongo store to be refreshed with github data, do a HTTP GET on localhost:xxxx/api/cache/reload.
  This will get the data from github and persists it all into your local mongo instance configured via the mongodb.uri config key (ie. application.conf) 

 N.B Please make sure mongod is running for the above to work

