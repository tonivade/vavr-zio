/*
 * Copyright (c) 2019, Antonio Gabriel Muñoz Conejo <antoniogmc at gmail dot com>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.vavr;

import static io.vavr.Function1.constant;
import static io.vavr.Function1.identity;
import static io.vavr.concurrent.Future.DEFAULT_EXECUTOR;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedRunnable;
import io.vavr.Function0;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.concurrent.Future;
import io.vavr.control.Either;
import io.vavr.control.Try;

public interface ZIO<R, E, A> {

  ZIO<?, ?, Unit> UNIT = pure(Unit.unit());

  Either<E, A> provide(R env);

  default Future<Either<E, A>> toFuture(R env) {
    return toFuture(DEFAULT_EXECUTOR, env);
  }

  default Future<Either<E, A>> toFuture(Executor executor, R env) {
    return Future.of(executor, () -> provide(env));
  }

  default void provideAsync(R env, Consumer<? super Try<Either<E, A>>> consumer) {
    provideAsync(DEFAULT_EXECUTOR, env, consumer);
  }

  default void provideAsync(Executor executor, R env, Consumer<? super Try<Either<E, A>>> consumer) {
    toFuture(executor, env).onComplete(consumer);
  }

  default <B> ZIO<R, E, B> map(Function1<A, B> map) {
    return new FlatMapped<>(this, ZIO::failure, map.andThen(ZIO::pure));
  }

  default <B> ZIO<R, E, B> flatMap(Function1<A, ZIO<R, E, B>> map) {
    return new FlatMapped<>(this, ZIO::failure, map);
  }

  default ZIO<R, A, E> swap() {
    return new Swap<>(this);
  }

  default <F> ZIO<R, F, A> mapError(Function1<E, F> map) {
    return new FlatMapped<>(this, map.andThen(ZIO::failure), ZIO::pure);
  }

  default <F> ZIO<R, F, A> flatMapError(Function1<E, ZIO<R, F, A>> map) {
    return new FlatMapped<>(this, map, ZIO::pure);
  }

  default <B, F> ZIO<R, F, B> bimap(Function1<E, F> mapError, Function1<A, B> map) {
    return new FlatMapped<>(this, mapError.andThen(ZIO::failure), map.andThen(ZIO::pure));
  }

  default <B> ZIO<R, E, B> andThen(Function0<ZIO<R, E, B>> next) {
    return flatMap(ignore -> next.get());
  }

  default <B, F> ZIO<R, F, B> foldM(Function1<E, ZIO<R, F, B>> mapError, Function1<A, ZIO<R, F, B>> map) {
    return new FoldM<>(this, mapError, map);
  }

  default <B> ZIO<R, Nothing, B> fold(Function1<E, B> mapError, Function1<A, B> map) {
    return foldM(mapError.andThen(ZIO::pure), map.andThen(ZIO::pure));
  }

  default ZIO<R, E, A> orElse(Function0<ZIO<R, E, A>> other) {
    return foldM(ignore -> other.get(), constant(this));
  }

  @SuppressWarnings("exports")
  ZIOModule getModule();

  static <R, E, A> ZIO<R, E, A> accessM(Function1<R, ZIO<R, E, A>> map) {
    return new AccessM<>(map);
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
    return value.flatMap(either -> either.fold(ZIO::failure, ZIO::pure));
  }

  static <R, A, B> Function1<A, ZIO<R, Throwable, B>> lift(CheckedFunction1<A, B> function) {
    return value -> from(() -> function.apply(value));
  }

  static <R, E, A> ZIO<R, E, A> from(Function0<Either<E, A>> task) {
    return new Task<>(task);
  }

  static <R, A> ZIO<R, Throwable, A> from(CheckedFunction0<A> task) {
    return new Attemp<>(task);
  }

  static <R> ZIO<R, Throwable, Unit> exec(CheckedRunnable task) {
    return new Attemp<>(() -> { task.run(); return Unit.unit(); });
  }

  static <R, E, A> ZIO<R, E, A> task(Function0<A> task) {
    return new Task<>(task.andThen(Either::right));
  }

  static <R, E, A> ZIO<R, E, A> pure(A value) {
    return new Pure<>(value);
  }

  static <R, E, A> ZIO<R, E, A> failure(E error) {
    return new Failure<>(error);
  }

  @SuppressWarnings("unchecked")
  static <R, E> ZIO<R, E, Unit> unit() {
    return (ZIO<R, E, Unit>) UNIT;
  }

  final class Pure<R, E, A> implements ZIO<R, E, A> {

    private A value;

    private Pure(A value) {
      this.value = requireNonNull(value);
    }

    @Override
    public Either<E, A> provide(R env) {
      return Either.right(value);
    }

    @SuppressWarnings("exports")
    @Override
    public ZIOModule getModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "Pure(" + value + ")";
    }
  }

  final class Failure<R, E, A> implements ZIO<R, E, A> {

    private E error;

    private Failure(E error) {
      this.error = requireNonNull(error);
    }

    @Override
    public Either<E, A> provide(R env) {
      return Either.left(error);
    }

    @SuppressWarnings("exports")
    @Override
    public ZIOModule getModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "Failure(" + error + ")";
    }
  }

  final class FlatMapped<R, E, A, F, B> implements ZIO<R, F, B> {

    private ZIO<R, E, A> current;
    private Function1<E, ZIO<R, F, B>> nextError;
    private Function1<A, ZIO<R, F, B>> next;

    private FlatMapped(ZIO<R, E, A> current,
                       Function1<E, ZIO<R, F, B>> nextError,
                       Function1<A, ZIO<R, F, B>> next) {
      this.current = requireNonNull(current);
      this.nextError = requireNonNull(nextError);
      this.next = requireNonNull(next);
    }

    @Override
    public Either<F, B> provide(R env) {
      var fold = current.provide(env).bimap(nextError, next);
      ZIO<R, F, B> result = fold.fold(identity(), identity());
      return result.provide(env);
    }

    @SuppressWarnings("exports")
    @Override
    public ZIOModule getModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "FlatMapped(" + current + ", ?, ?)";
    }
  }

  final class Task<R, E, A> implements ZIO<R, E, A> {

    private Function0<Either<E, A>> task;

    private Task(Function0<Either<E, A>> task) {
      this.task = requireNonNull(task);
    }

    @Override
    public Either<E, A> provide(R env) {
      return task.apply();
    }

    @SuppressWarnings("exports")
    @Override
    public ZIOModule getModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "Task(?)";
    }
  }

  final class Swap<R, E, A> implements ZIO<R, A, E> {

    private ZIO<R, E, A> current;

    private Swap(ZIO<R, E, A> current) {
      this.current = requireNonNull(current);
    }

    @Override
    public Either<A, E> provide(R env) {
      return current.provide(env).swap();
    }

    @SuppressWarnings("exports")
    @Override
    public ZIOModule getModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "Swap(" + current + ")";
    }
  }

  final class Attemp<R, A> implements ZIO<R, Throwable, A> {

    private final CheckedFunction0<A> current;

    private Attemp(CheckedFunction0<A> current) {
      this.current = requireNonNull(current);
    }

    @Override
    public Either<Throwable, A> provide(R env) {
      return Try.of(current).toEither();
    }

    @SuppressWarnings("exports")
    @Override
    public ZIOModule getModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "Attemp(" + current + ")";
    }
  }

  final class AccessM<R, E, A> implements ZIO<R, E, A> {

    private Function1<R, ZIO<R, E, A>> function;

    private AccessM(Function1<R, ZIO<R, E, A>> function) {
      this.function = requireNonNull(function);
    }

    @Override
    public Either<E, A> provide(R env) {
      return function.apply(env).provide(env);
    }

    @SuppressWarnings("exports")
    @Override
    public ZIOModule getModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "AccessM(?)";
    }
  }

  final class FoldM<R, E, A, F, B> implements ZIO<R, F, B> {

    private ZIO<R, E, A> current;
    private Function1<E, ZIO<R, F, B>> nextError;
    private Function1<A, ZIO<R, F, B>> next;

    private FoldM(ZIO<R, E, A> current, Function1<E, ZIO<R, F, B>> nextError, Function1<A, ZIO<R, F, B>> next) {
      this.current = requireNonNull(current);
      this.nextError = requireNonNull(nextError);
      this.next = requireNonNull(next);
    }

    @Override
    public Either<F, B> provide(R env) {
      return current.provide(env).fold(nextError, next).provide(env);
    }

    @SuppressWarnings("exports")
    @Override
    public ZIOModule getModule() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "FoldM(" + current + ", ?, ?)";
    }
  }
}

interface ZIOModule { }