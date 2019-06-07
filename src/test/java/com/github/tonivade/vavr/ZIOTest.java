/*
 * Copyright (c) 2019, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.vavr;

import static com.github.tonivade.vavr.Nothing.nothing;
import static io.vavr.Function1.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.vavr.control.Either;

public class ZIOTest {

  @Test
  public void mapRight() {
    var result = parseInt("1").map(x -> x + 1).provide(nothing());

    assertEquals(Either.right(2), result);
  }

  @Test
  public void mapLeft() {
    var result = parseInt("lskjdf").map(x -> x + 1).provide(nothing());

    assertEquals(NumberFormatException.class, result.getLeft().getClass());
  }

  @Test
  public void mapError() {
    var result = parseInt("lskjdf").mapError(Throwable::getMessage).provide(nothing());

    assertEquals(Either.left("For input string: \"lskjdf\""), result);
  }

  @Test
  public void flatMapRight() {
    var result = parseInt("1").flatMap(x -> ZIO.pure(x + 1)).provide(nothing());

    assertEquals(Either.right(2), result);
  }

  @Test
  public void flatMapLeft() {
    var result = parseInt("lskjdf").flatMap(x -> ZIO.pure(x + 1)).provide(nothing());

    assertEquals(NumberFormatException.class, result.getLeft().getClass());
  }

  @Test
  public void flatMapError() {
    var result = parseInt("lskjdf").flatMapError(e -> ZIO.failure(e.getMessage())).provide(nothing());

    assertEquals(Either.left("For input string: \"lskjdf\""), result);
  }

  @Test
  public void bimapRight() {
    var result = parseInt("1").bimap(Throwable::getMessage, x -> x + 1).provide(nothing());

    assertEquals(Either.right(2), result);
  }

  @Test
  public void bimapLeft() {
    var result = parseInt("lskjdf").bimap(Throwable::getMessage, x -> x + 1).provide(nothing());

    assertEquals(Either.left("For input string: \"lskjdf\""), result);
  }

  @Test
  public void foldRight() {
    var result = parseInt("1").fold(e -> -1, identity()).provide(nothing());

    assertEquals(Either.right(1), result);
  }

  @Test
  public void foldLeft() {
    var result = parseInt("kjsdfdf").fold(e -> -1, identity()).provide(nothing());

    assertEquals(Either.right(-1), result);
  }

  @Test
  public void orElseRight() {
    var result = parseInt("1").orElse(() -> ZIO.pure(2)).provide(nothing());

    assertEquals(Either.right(1), result);
  }

  @Test
  public void orElseLeft() {
    var result = parseInt("kjsdfe").orElse(() -> ZIO.pure(2)).provide(nothing());

    assertEquals(Either.right(2), result);
  }

  @Test
  public void swap() {
    var result = parseInt("asdfj").swap().provide(nothing());

    assertTrue(result.get() instanceof NumberFormatException);
  }

  @Test
  public void map2Right() {
    var result = ZIO.map2(parseInt("1"), parseInt("2"), (a, b) -> a + b);

    assertEquals(Either.right(3), result.provide(nothing()));
  }

  @Test
  public void map2Left() {
    var result = ZIO.map2(parseInt("1"), parseInt("jksdf"), (a, b) -> a + b).mapError(Throwable::getMessage);

    assertEquals(Either.left("For input string: \"jksdf\""), result.provide(nothing()));
  }

  private ZIO<Nothing, Throwable, Integer> parseInt(String string) {
    return ZIO.from(() -> Integer.parseInt(string));
  }
}
