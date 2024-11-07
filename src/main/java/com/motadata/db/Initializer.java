package com.motadata.db;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class Initializer
{
    private final MongoClient mongoClient;

    public Initializer(Vertx vertx)
    {
        this.mongoClient = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "nms_db"));
    }

    public MongoClient getMongoClient()
    {
        return mongoClient;
    }
}
