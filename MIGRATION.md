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
