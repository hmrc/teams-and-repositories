# Migration to 11.23.0

Data model changes from teams with repos, to repos with teams. 

```javascript
db.getCollection('teamsAndRepositories').aggregate([
     {$unwind: "$repositories"}
    ,{$group: {
        _id: "$repositories.name", 
        repositories: {$max: "$repositories"},
        teams: {$addToSet: "$teamName"},
        }
     }
    ,{$addFields: {"repositories.teamNames":"$teams" }}
    ,{$replaceRoot: {newRoot: "$repositories"} }
    ,{$addFields: {"isArchived": "$archived"}}
    ,{$project: {archived: 0}}
    ,{$out: "repositories"}
])
```

# Migration to 11.0.0

```javascript
db.getCollection('updateTime').drop()
```

## Use Date rather than Long

```javascript
db.getCollection('teamsAndRepositories').renameCollection('teamsAndRepositories-bak')
```

```javascript
db.getCollection('teamsAndRepositories-bak').aggregate([
  {
    $addFields: {
      repositories: {
        $map: {
          input: '$repositories',
          as: 'repository',
          in: {
            $mergeObjects: [
              "$$repository",
              {
                createdDate: { $toDate: "$$repository.createdDate" },
                lastActiveDate: { $toDate: "$$repository.lastActiveDate" }
              }
            ]
          }
        }
      },
      updateDate: { $toDate: "$updateDate"},
    }
  },
  { $out: "teamsAndRepositories" }
]);
```

