package dev.faustin0

import software.amazon.awssdk.services.dynamodb.model._

object DynamoSetUp {

  object BusStopTable {

    def createTableRequest: CreateTableRequest = CreateTableRequest
      .builder()
      .tableName("bus_stops")
      .attributeDefinitions(
        AttributeDefinition
          .builder()
          .attributeName("code")
          .attributeType(ScalarAttributeType.N)
          .build(),
        AttributeDefinition
          .builder()
          .attributeName("name")
          .attributeType(ScalarAttributeType.S)
          .build()
      )
      .keySchema(
        KeySchemaElement
          .builder()
          .attributeName("code")
          .keyType(KeyType.HASH)
          .build()
      )
      .provisionedThroughput(
        ProvisionedThroughput
          .builder()
          .readCapacityUnits(5)
          .writeCapacityUnits(14)
          .build()
      )
      .globalSecondaryIndexes(
        GlobalSecondaryIndex
          .builder()
          .indexName("name-index")
          .projection(
            Projection
              .builder()
              .projectionType(ProjectionType.ALL)
              .build()
          )
          .keySchema(
            KeySchemaElement
              .builder()
              .attributeName("name")
              .keyType(KeyType.HASH)
              .build()
          )
          .provisionedThroughput(
            ProvisionedThroughput
              .builder()
              .readCapacityUnits(5)
              .writeCapacityUnits(7)
              .build()
          )
          .build()
      )
      .build()

  }

}
