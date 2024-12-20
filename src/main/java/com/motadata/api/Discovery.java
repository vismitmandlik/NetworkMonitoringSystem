package com.motadata.api;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class Discovery
{
    public static void initRoutes(Router router)
    {
        router.post("/api/discovery").handler(BodyHandler.create()).handler(context -> context.vertx().eventBus().request("discovery.request", context.body().asJsonObject(),reply ->
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
