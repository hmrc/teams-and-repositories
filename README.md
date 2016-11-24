teams-and-services
==================

API for retrieving repositories belonging to teams.


Offline mode
============
When developing/working on teams-and-repositories locally, you can configure your server to load the github data from your local file system.
To do this, you need to have a the github data saved/available in a file on your local file sytem. see the [Saving github data] section below.
 
 
Saving github data
----------
 
 To save the github data to disk, you need to first get and cache the data from github api and then save it to disk. 
 * uncomment the following line in app.routes file
 ```
  #GET        /api/save                            @uk.gov.hmrc.teamsandrepositories.DataSavingController.save
```
 * Set the github.offline.mode in application.conf to false (removing that property all together has the same effect)
 * Start the teams-and-repositories server (ie: port 9000) and go to this url:
  `http://localhost:9000/api/save?file=<path-to-save-file>`
 
 `eg: http://localhost:9000/api/save?file=%2Ftmp%2Fgithub.data`
will save the data to file /tmp/github.data
 
Set up for offline mode
------
    
    
    
To work with offline data:
 * set the github.offline.mode = true 
 * specify cacheFilename in the application.config
 * start the server
 eg: github.offline.mode = true
     cacheFilename = "/development/teams-and-repositories/misc/github.json"
    
   
  