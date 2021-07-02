# Migration to 11.0.0


## Add new collections

### Convert dates from long to date

Is collection updateTime ever used?


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
