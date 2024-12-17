package com.motadata.db;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class Initializer
{
    private static MongoClient mongoClient;

    private final Vertx vertx = Vertx.vertx();

    public void initMongoClient()
    {
        mongoClient = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "nms_db"));

        mongoClient.runCommand("ping", new JsonObject().put("ping", 1), res ->
        {
            if (res.succeeded())
            {
                System.out.println("Connected to MongoDB  " );
            }
            else
            {
                System.err.println("Failed to connect: " + res.cause().getMessage());
            }
        });
    }

    public static MongoClient getMongoClient()
    {
        return mongoClient;
    }


}
