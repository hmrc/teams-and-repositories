# Migration to 11.171.0

```javascript
db.getCollection('deleted-repositories').updateMany(
  {},
  {$set: {"organisation": "mdtp"}}
);
```

# Migration to 11.102.0

`prototypeUrl` renamed to `prototypeName`

```javascript
db.repositories.updateMany({}, { $rename: { "prototypeUrl": "prototypeName" } });
```

# Migration to 11.97.0

ServiceType data model `FrontendService` -> `frontend`, `BackendService` -> `backend`

```javascript
db.repositories.updateMany(
  { "serviceType": "BackendService" },
  { $set: { "serviceType": "backend" } }
);

db.repositories.updateMany(
  { "serviceType": "FrontendService" },
  { $set: { "serviceType": "frontend" } }
);
```

Tag data model `AdminFrontend` -> `admin`, `Stub` -> `stub`, `Api` -> `api`

```javascript
db.repositories.updateMany(
  { "tags": "Stub" },
  { $set: { "tags.$[element]": "stub" } },
  { arrayFilters: [{ "element": "Stub" }] }
);

db.repositories.updateMany(
  { "tags": "AdminFrontend" },
  { $set: { "tags.$[element]": "admin" } },
  { arrayFilters: [{ "element": "AdminFrontend" }] }
);

db.repositories.updateMany(
  { "tags": "Api" },
  { $set: { "tags.$[element]": "api" } },
  { arrayFilters: [{ "element": "Api" }] }
);
```

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

