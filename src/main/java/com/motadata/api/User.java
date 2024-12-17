package com.motadata.api;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class User {

    // Initialize routes and register them in the Router
    public static void initRoutes(Router router)
    {
        // Setup routes
        router.post("/api/user/register").handler(BodyHandler.create()).handler(com.motadata.services.User::register);

        router.post("/api/user/login").handler(BodyHandler.create()).handler(com.motadata.services.User::login);
    }
}
