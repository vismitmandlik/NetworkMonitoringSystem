package com.motadata.db;

import com.motadata.Main;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class Initializer
{
    private static MongoClient mongoClient;

    public static void initMongoClient(JsonObject config)
    {
        var connectionString = config.getString("connection_string", "mongodb://localhost:27017");

        var dbName = config.getString("db_name", "nms_db");

        mongoClient = MongoClient.createShared(Main.vertx(), new JsonObject().put("connection_string", connectionString).put("db_name", dbName));

        mongoClient.runCommand("ping", new JsonObject().put("ping", 1), response ->
        {
            if (response.succeeded())
            {
                System.out.println("Connected to MongoDB  " );
            }
            else
            {
                System.err.println("Failed to connect: " + response.cause().getMessage());
            }
        });
    }

    public static MongoClient getMongoClient()
    {
        return mongoClient;
    }
}
