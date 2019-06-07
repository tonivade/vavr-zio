/*
 * Copyright (c) 2019, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.vavr;

import static com.github.tonivade.vavr.Nothing.nothing;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.vavr.control.Either;

public class RefTest {

  @Test
  public void get() {
    var ref = Ref.of("Hello World!");

    var result = ref.get();

    assertEquals(Either.right("Hello World!"), result.provide(nothing()));
  }

  @Test
  public void set() {
    var ref = Ref.of("Hello World!");

    var result = ref.set("Something else").andThen(ref.get());

    assertEquals(Either.right("Something else"), result.provide(nothing()));
  }

  @Test
  public void lazySet() {
    var ref = Ref.of("Hello World!");

    var result = ref.lazySet("Something else").andThen(ref.get());

    assertEquals(Either.right("Something else"), result.provide(nothing()));
  }

  @Test
  public void getAndSet() {
    var ref = Ref.of("Hello World!");

    var result = ref.getAndSet("Something else");
    var afterUpdate = result.andThen(ref.get());

    assertEquals(Either.right("Hello World!"), result.provide(nothing()));
    assertEquals(Either.right("Something else"), afterUpdate.provide(nothing()));
  }

  @Test
  public void getAndUpdate() {
    var ref = Ref.of("Hello World!");

    var result = ref.getAndUpdate(String::toUpperCase);
    var afterUpdate = result.andThen(ref.get());

    assertEquals(Either.right("Hello World!"), result.provide(nothing()));
    assertEquals(Either.right("HELLO WORLD!"), afterUpdate.provide(nothing()));
  }

  @Test
  public void updateAndGet() {
    var ref = Ref.of("Hello World!");

    var result = ref.updateAndGet(String::toUpperCase);

    assertEquals(Either.right("HELLO WORLD!"), result.provide(nothing()));
  }
}
