package com.motadata.api;

import io.vertx.ext.web.Router;

public class User
{
    // Initialize routes and register them in the Router
    public static void initRoutes(Router router)
    {
        router.post("/register").handler(com.motadata.services.User::register);

        router.post("/login").handler(com.motadata.services.User::login);
    }
}
