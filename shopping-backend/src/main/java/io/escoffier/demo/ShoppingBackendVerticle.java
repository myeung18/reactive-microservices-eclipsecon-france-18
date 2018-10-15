package io.escoffier.demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.redis.RedisClient;

import java.util.HashMap;
import java.util.Map;

public class ShoppingBackendVerticle extends AbstractVerticle {

  private RedisClient redis;
  private Map<String, Integer> list = new HashMap<>();

  @Override
  public void start() {
    Router router = Router.router(vertx);

    router.get("/").handler(rc -> rc.response().end("Bonjour!"));
    router.get("/shopping").handler(this::getList);

    router.route().handler(BodyHandler.create());

    router.post("/shopping").handler(this::addToList);
    router.delete("/shopping/:name").handler(this::removeFromList);

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);
  }

  private void removeFromList(RoutingContext rc) {
    String name = rc.pathParam("name");
    list.remove(name);

    getList(rc);
  }

  private void addToList(RoutingContext rc) {
    JsonObject body = rc.getBodyAsJson();
    String name = body.getString("name");
    Integer quantity = body.getInteger("quantity", 1);
    list.put(name, quantity);
    getList(rc);

  }

  private void getList(RoutingContext rc) {
    rc.response().end(Json.encodePrettily(list));
  }

}
