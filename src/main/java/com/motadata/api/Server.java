package com.motadata.api;

import com.motadata.configs.Auth;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class Server extends AbstractVerticle
{
    private Router router;

    @Override
    public void start()
    {
        // Get configuration from the Vert.x context (set in Main)
        JsonObject config = vertx.getOrCreateContext().config();

        if (config == null)
        {
            System.err.println("Failed to load configuration");

            return;
        }

        System.out.println("Loaded all Configs");

        Auth.initialize(vertx,config);

        var port = config.getInteger("http_port", 8080);

        router = Router.router(vertx);

        setupRoutes();

        vertx.createHttpServer().requestHandler(router).listen(port, res ->
        {
            if (res.succeeded())
            {
                System.out.println("Server started on port 8080");
            }
            else
            {
                System.err.println("Failed to start server: " + res.cause());
            }
        });
    }

    private void setupRoutes()
    {

        User.initRoutes(router);

        CredentialProfile.initRoutes(router);

        Discovery.initRoutes(router);

        Object.initRoutes(router);

    }
}
