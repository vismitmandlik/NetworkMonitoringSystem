package com.motadata.api;

import com.motadata.configs.Auth;
import com.motadata.services.ObjectManager;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class Object
{
    private static final JWTAuth JWT_AUTH = Auth.jwtAuth();

    public static void initRoutes(Router router)
    {
        router.post("/api/object/provision").handler(BodyHandler.create()).handler(JWTAuthHandler.create(JWT_AUTH)).handler(ObjectManager::provisionDevices);

        router.post("/api/object/poll").handler(BodyHandler.create()).handler(JWTAuthHandler.create(JWT_AUTH)).handler(ObjectManager::provisionDevices);
    }
}
