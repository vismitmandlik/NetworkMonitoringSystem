package com.motadata.api;

import com.motadata.configs.Auth;
import com.motadata.constants.Constants;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class Server extends AbstractVerticle
{
    private Router router;

    @Override
    public void start()
    {
        // Get configuration from the Vert.x context (set in Main)
        var config = vertx.getOrCreateContext().config();

        if (config == null)
        {
            System.err.println("Failed to load configuration");

            return;
        }

        System.out.println("Loaded all Configs");

        Auth.initialize(vertx,config);

        router = Router.router(vertx);

        var port = config.getInteger("http_port", Constants.HTTP_PORT);

        vertx.createHttpServer().requestHandler(router).listen(port, response ->
        {
            if (response.succeeded())
            {
                System.out.println("Server started on port 8080");
            }
            else
            {
                System.err.println("Failed to start server: " + response.cause());
            }
        });

        setupRoutes();
    }

    private void setupRoutes()
    {
        // Apply BodyHandler and JWTAuthHandler globally to all routes
        router.route().handler(BodyHandler.create());

        // Creating sub-routers for each resource
        var userRouter = Router.router(vertx);

        var credentialRouter = Router.router(vertx);

        var discoveryRouter = Router.router(vertx);

        var objectRouter = Router.router(vertx);

        router.route("/api/user/*").subRouter(userRouter);

        router.route().handler(JWTAuthHandler.create(Auth.jwtAuth()));

        router.route("/api/credentials/*").subRouter(credentialRouter);

        router.route("/api/discovery/*").subRouter(discoveryRouter);

        router.route("/api/object/*").subRouter(objectRouter);

        // Initialize routes for each resource
        User.init(userRouter);

        CredentialProfile.init(credentialRouter);

        Discovery.init(discoveryRouter);

        Object.init(objectRouter);

    }
}
