package com.motadata.api;

import com.motadata.configs.Auth;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class Discovery
{
    private static final JWTAuth JWT_AUTH = Auth.jwtAuth();

    public static void initRoutes(Router router)
    {
        router.post("/api/discovery").handler(JWTAuthHandler.create(JWT_AUTH)).handler(context -> context.vertx().eventBus().request("discovery.request", context.body().asJsonObject(), reply ->
                {
                    if (reply.succeeded())
                    {
                        context.response().setStatusCode(200).end(((JsonObject) reply.result().body()).encodePrettily());
                    }
                    else
                    {
                        context.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to start discovery").encodePrettily());
                    }
                }));
    }
}
