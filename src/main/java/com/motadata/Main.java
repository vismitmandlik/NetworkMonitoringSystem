package com.motadata;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import com.motadata.api.Server;
import com.motadata.db.Initializer;

public class Main extends AbstractVerticle
{
    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        Initializer initializer = new Initializer();

        initializer.initMongoClient();

        DeploymentOptions optionsForMyVerticle = new DeploymentOptions();

        vertx.deployVerticle(Server.class.getName(), optionsForMyVerticle, res ->
        {
            if (res.succeeded())
            {
                System.out.println("Successfully deployed Server Verticle");
            }
            else
            {
                System.out.println("Failed to deploy MyVerticle: " + res.cause());
            }
        });
    }
}
