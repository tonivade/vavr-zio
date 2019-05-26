/*
 * Copyright (c) 2018-2019, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.vavr;

import static com.github.tonivade.vavr.Nothing.nothing;
import static io.vavr.Function1.constant;
import static io.vavr.Function1.identity;

import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedRunnable;
import io.vavr.Function0;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.control.Either;
import io.vavr.control.Try;

@FunctionalInterface
public interface ZIO<R, E, A> {

  ZIO<?, ?, Nothing> UNIT = pure(nothing());

  Either<E, A> provide(R env);

  default <B> ZIO<R, E, B> map(Function1<A, B> map) {
    return mapValue(value -> value.map(map));
  }

  default <B> ZIO<R, E, B> flatMap(Function1<A, ZIO<R, E, B>> map) {
    return flatMapValue(value -> value.map(map).fold(ZIO::raiseError, identity()));
  }

  default ZIO<R, A, E> swap() {
    return mapValue(Either<E, A>::swap);
  }

  default <B> ZIO<R, B, A> mapError(Function1<E, B> map) {
    return mapValue(value -> value.mapLeft(map));
  }

  default <F> ZIO<R, F, A> flatMapError(Function1<E, ZIO<R, F, A>> map) {
    return flatMapValue(value -> value.mapLeft(map).fold(identity(), ZIO::pure));
  }

  default <B, F> ZIO<R, F, B> bimap(Function1<E, F> mapError, Function1<A, B> map) {
    return mapValue(value -> value.bimap(mapError, map));
  }

  default <B> ZIO<R, E, B> andThen(Function0<ZIO<R, E, B>> next) {
    return flatMap(ignore -> next.get());
  }

  default <B, F> ZIO<R, F, B> foldM(Function1<E, ZIO<R, F, B>> mapError, Function1<A, ZIO<R, F, B>> map) {
    return env -> provide(env).fold(mapError, map).provide(env);
  }

  default <B> ZIO<R, Nothing, B> fold(Function1<E, B> mapError, Function1<A, B> map) {
    return foldM(mapError.andThen(ZIO::pure), map.andThen(ZIO::pure));
  }

  default ZIO<R, E, A> orElse(Function0<ZIO<R, E, A>> other) {
    return foldM(ignore -> other.get(), constant(this));
  }

  static <R, E, A> ZIO<R, E, A> accessM(Function1<R, ZIO<R, E, A>> map) {
    return env -> map.apply(env).provide(env);
  }

  static <R, A> ZIO<R, Nothing, A> access(Function1<R, A> map) {
    return accessM(map.andThen(ZIO::pure));
  }

  static <R> ZIO<R, Nothing, R> env() {
    return access(identity());
  }

  static <R, E, A, B, C> ZIO<R, E, C> map2(ZIO<R, E, A> za, ZIO<R, E, B> zb, Function2<A, B, C> mapper) {
    return za.flatMap(a -> zb.map(b -> mapper.curried().apply(a).apply(b)));
  }

  static <R, E, A> ZIO<R, E, A> absorb(ZIO<R, E, Either<E, A>> value) {
    return value.flatMap(either -> either.fold(ZIO::raiseError, ZIO::pure));
  }

  static <R, A, B> Function1<A, ZIO<R, Throwable, B>> lift(CheckedFunction1<A, B> function) {
    return value -> from(() -> function.apply(value));
  }

  static <R, E, A> ZIO<R, E, A> from(Function0<Either<E, A>> task) {
    return env -> task.get();
  }

  static <R, A> ZIO<R, Throwable, A> from(CheckedFunction0<A> task) {
    return env -> Try.of(task).toEither();
  }

  static <R> ZIO<R, Throwable, Nothing> exec(CheckedRunnable task) {
    return from(() -> { task.run(); return nothing(); });
  }

  static <R, E, A> ZIO<R, E, A> pure(A value) {
    return env -> Either.right(value);
  }

  static <R, E, A> ZIO<R, E, A> raiseError(E error) {
    return env -> Either.left(error);
  }

  @SuppressWarnings("unchecked")
  static <R, E> ZIO<R, E, Nothing> unit() {
    return (ZIO<R, E, Nothing>) UNIT;
  }

  private <F, B> ZIO<R, F, B> mapValue(Function1<Either<E, A>, Either<F, B>> map) {
    return env -> map.apply(this.provide(env));
  }

  private <F, B> ZIO<R, F, B> flatMapValue(Function1<Either<E, A>, ZIO<R, F, B>> map) {
    return env -> map.apply(this.provide(env)).provide(env);
  }
}