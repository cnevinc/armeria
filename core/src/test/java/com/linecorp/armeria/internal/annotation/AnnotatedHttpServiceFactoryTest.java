/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.internal.annotation;

import static com.linecorp.armeria.internal.annotation.AnnotatedHttpServiceFactory.collectDecorators;
import static com.linecorp.armeria.internal.annotation.AnnotatedHttpServiceFactory.create;
import static com.linecorp.armeria.internal.annotation.AnnotatedHttpServiceFactory.find;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.util.Lists;
import org.junit.Test;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.logging.LogLevel;
import com.linecorp.armeria.internal.annotation.AnnotatedHttpServiceFactory.DecoratorAndOrder;
import com.linecorp.armeria.server.DecoratingServiceFunction;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;
import com.linecorp.armeria.server.annotation.Decorator;
import com.linecorp.armeria.server.annotation.DecoratorFactory;
import com.linecorp.armeria.server.annotation.DecoratorFactoryFunction;
import com.linecorp.armeria.server.annotation.Delete;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Head;
import com.linecorp.armeria.server.annotation.Options;
import com.linecorp.armeria.server.annotation.Patch;
import com.linecorp.armeria.server.annotation.PathPrefix;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.Put;
import com.linecorp.armeria.server.annotation.Trace;
import com.linecorp.armeria.server.annotation.decorator.LoggingDecorator;
import com.linecorp.armeria.server.annotation.decorator.LoggingDecoratorFactoryFunction;
import com.linecorp.armeria.server.annotation.decorator.RateLimitingDecorator;
import com.linecorp.armeria.server.annotation.decorator.RateLimitingDecoratorFactoryFunction;

public class AnnotatedHttpServiceFactoryTest {

    private static final String HOME_PATH_PREFIX = "/home";

    @Test
    public void ofNoOrdering() throws NoSuchMethodException {
        final List<DecoratorAndOrder> list =
                collectDecorators(TestClass.class,
                                  TestClass.class.getMethod("noOrdering"));
        assertThat(values(list)).containsExactly(Decorator1.class,
                                                 LoggingDecoratorFactoryFunction.class,
                                                 LoggingDecoratorFactoryFunction.class,
                                                 Decorator2.class);
        assertThat(orders(list)).containsExactly(0, 0, 0, 0);

        final LoggingDecorator info = (LoggingDecorator) list.get(1).annotation();
        assertThat(info.requestLogLevel()).isEqualTo(LogLevel.INFO);
        final LoggingDecorator trace = (LoggingDecorator) list.get(2).annotation();
        assertThat(trace.requestLogLevel()).isEqualTo(LogLevel.TRACE);
    }

    @Test
    public void ofMethodScopeOrdering() throws NoSuchMethodException {
        final List<DecoratorAndOrder> list =
                collectDecorators(TestClass.class,
                                  TestClass.class.getMethod("methodScopeOrdering"));
        assertThat(values(list)).containsExactly(Decorator1.class,
                                                 LoggingDecoratorFactoryFunction.class,
                                                 RateLimitingDecoratorFactoryFunction.class,
                                                 Decorator1.class,
                                                 LoggingDecoratorFactoryFunction.class,
                                                 Decorator2.class);
        assertThat(orders(list)).containsExactly(0, 0, 0, 1, 2, 3);

        final LoggingDecorator info = (LoggingDecorator) list.get(1).annotation();
        assertThat(info.requestLogLevel()).isEqualTo(LogLevel.INFO);
        final RateLimitingDecorator limit = (RateLimitingDecorator) list.get(2).annotation();
        assertThat(limit.value()).isEqualTo(1);
        final LoggingDecorator trace = (LoggingDecorator) list.get(4).annotation();
        assertThat(trace.requestLogLevel()).isEqualTo(LogLevel.TRACE);
    }

    @Test
    public void ofGlobalScopeOrdering() throws NoSuchMethodException {
        final List<DecoratorAndOrder> list =
                collectDecorators(TestClass.class,
                                  TestClass.class.getMethod("globalScopeOrdering"));
        assertThat(values(list)).containsExactly(LoggingDecoratorFactoryFunction.class,
                                                 Decorator1.class,
                                                 LoggingDecoratorFactoryFunction.class,
                                                 Decorator2.class);
        assertThat(orders(list)).containsExactly(-1, 0, 0, 1);

        final LoggingDecorator trace = (LoggingDecorator) list.get(0).annotation();
        assertThat(trace.requestLogLevel()).isEqualTo(LogLevel.TRACE);
        final LoggingDecorator info = (LoggingDecorator) list.get(2).annotation();
        assertThat(info.requestLogLevel()).isEqualTo(LogLevel.INFO);
    }

    @Test
    public void ofUserDefinedRepeatableDecorator() throws NoSuchMethodException {
        final List<DecoratorAndOrder> list =
                collectDecorators(TestClass.class,
                                  TestClass.class.getMethod("userDefinedRepeatableDecorator"));
        assertThat(values(list)).containsExactly(Decorator1.class,
                                                 LoggingDecoratorFactoryFunction.class,
                                                 UserDefinedRepeatableDecoratorFactory.class,
                                                 Decorator2.class,
                                                 UserDefinedRepeatableDecoratorFactory.class);
        assertThat(orders(list)).containsExactly(0, 0, 1, 2, 3);

        final LoggingDecorator info = (LoggingDecorator) list.get(1).annotation();
        assertThat(info.requestLogLevel()).isEqualTo(LogLevel.INFO);
        final UserDefinedRepeatableDecorator udd1 = (UserDefinedRepeatableDecorator) list.get(2).annotation();
        assertThat(udd1.value()).isEqualTo(1);
        final UserDefinedRepeatableDecorator udd2 = (UserDefinedRepeatableDecorator) list.get(4).annotation();
        assertThat(udd2.value()).isEqualTo(2);
    }

    @Test
    public void testFindAnnotatedServiceElementsWithPathPrefixAnnotation() {
        final Object object = new PathPrefixServiceObject();
        final List<AnnotatedHttpServiceElement> elements = find("/", object, new ArrayList<>());

        final List<String> paths = elements.stream()
                                           .map(AnnotatedHttpServiceElement::route)
                                           .map(route -> route.paths().get(0))
                                           .collect(Collectors.toList());

        assertThat(paths).containsExactlyInAnyOrder(HOME_PATH_PREFIX + "/hello", HOME_PATH_PREFIX + '/');
    }

    @Test
    public void testFindAnnotatedServiceElementsWithoutPathPrefixAnnotation() {
        final Object serviceObject = new ServiceObject();
        final List<AnnotatedHttpServiceElement> elements = find(HOME_PATH_PREFIX, serviceObject,
                                                                new ArrayList<>());

        final List<String> paths = elements.stream()
                                           .map(AnnotatedHttpServiceElement::route)
                                           .map(route -> route.paths().get(0))
                                           .collect(Collectors.toList());

        assertThat(paths).containsExactlyInAnyOrder(HOME_PATH_PREFIX + "/hello", HOME_PATH_PREFIX + '/');
    }

    @Test
    public void testCreateAnnotatedServiceElementWithoutExplicitPathOnMethod() {
        final ServiceObjectWithoutPathOnAnnotatedMethod serviceObject =
                new ServiceObjectWithoutPathOnAnnotatedMethod();

        getMethods(ServiceObjectWithoutPathOnAnnotatedMethod.class, HttpResponse.class).forEach(method -> {
            assertThatThrownBy(() -> {
                create("/", serviceObject, method, Lists.emptyList(), Lists.emptyList(), Lists.emptyList());
            }).isInstanceOf(IllegalArgumentException.class)
              .hasMessage("A path pattern should be specified by @Path or HTTP method annotations.");
        });
    }

    private static List<Class<?>> values(List<DecoratorAndOrder> list) {
        return list.stream()
                   .map(DecoratorAndOrder::annotation)
                   .map(annotation -> {
                       if (annotation instanceof Decorator) {
                           return ((Decorator) annotation).value();
                       }
                       final DecoratorFactory factory =
                               annotation.annotationType().getAnnotation(DecoratorFactory.class);
                       if (factory != null) {
                           return factory.value();
                       }
                       throw new Error("Should not reach here.");
                   })
                   .collect(Collectors.toList());
    }

    private static List<Integer> orders(List<DecoratorAndOrder> list) {
        return list.stream().map(DecoratorAndOrder::order).collect(Collectors.toList());
    }

    static class Decorator1 implements DecoratingServiceFunction<HttpRequest, HttpResponse> {
        @Override
        public HttpResponse serve(Service<HttpRequest, HttpResponse> delegate, ServiceRequestContext ctx,
                                  HttpRequest req) throws Exception {
            return delegate.serve(ctx, req);
        }
    }

    static class Decorator2 implements DecoratingServiceFunction<HttpRequest, HttpResponse> {
        @Override
        public HttpResponse serve(Service<HttpRequest, HttpResponse> delegate, ServiceRequestContext ctx,
                                  HttpRequest req) throws Exception {
            return delegate.serve(ctx, req);
        }
    }

    static Stream<Method> getMethods(Class<?> clazz, Class<?> returnTypeClass) {
        final Method[] methods = clazz.getMethods();
        return Stream.of(methods).filter(method -> method.getReturnType() == returnTypeClass);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface UserDefinedRepeatableDecorators {
        UserDefinedRepeatableDecorator[] value();
    }

    @DecoratorFactory(UserDefinedRepeatableDecoratorFactory.class)
    @Repeatable(UserDefinedRepeatableDecorators.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @interface UserDefinedRepeatableDecorator {

        // To identify the decorator instance.
        int value();

        // For ordering.
        int order() default 0;
    }

    static class UserDefinedRepeatableDecoratorFactory
            implements DecoratorFactoryFunction<UserDefinedRepeatableDecorator> {
        @Override
        public Function<Service<HttpRequest, HttpResponse>,
                ? extends Service<HttpRequest, HttpResponse>> newDecorator(
                UserDefinedRepeatableDecorator parameter) {
            return service -> new SimpleDecoratingHttpService(service) {
                @Override
                public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
                    return service.serve(ctx, req);
                }
            };
        }
    }

    @Decorator(Decorator1.class)
    @LoggingDecorator(requestLogLevel = LogLevel.INFO)
    static class TestClass {

        @LoggingDecorator
        @Decorator(Decorator2.class)
        public String noOrdering() {
            return "";
        }

        @RateLimitingDecorator(1)
        @Decorator(value = Decorator1.class, order = 1)
        @LoggingDecorator(order = 2)
        @Decorator(value = Decorator2.class, order = 3)
        public String methodScopeOrdering() {
            return "";
        }

        @Decorator(value = Decorator2.class, order = 1)
        @LoggingDecorator(order = -1)
        public String globalScopeOrdering() {
            return "";
        }

        @UserDefinedRepeatableDecorator(value = 1, order = 1)
        @Decorator(value = Decorator2.class, order = 2)
        @UserDefinedRepeatableDecorator(value = 2, order = 3)
        public String userDefinedRepeatableDecorator() {
            return "";
        }
    }

    @PathPrefix(HOME_PATH_PREFIX)
    static class PathPrefixServiceObject {

        @Get("/hello")
        public HttpResponse get() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Post("/")
        public HttpResponse post() {
            return HttpResponse.of(HttpStatus.OK);
        }
    }

    static class ServiceObject {

        @Get("/hello")
        public HttpResponse get() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Post("/")
        public HttpResponse post() {
            return HttpResponse.of(HttpStatus.OK);
        }
    }

    @PathPrefix("/")
    static class ServiceObjectWithoutPathOnAnnotatedMethod {

        @Post
        public HttpResponse post() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Get
        public HttpResponse get() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Head
        public HttpResponse head() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Put
        public HttpResponse put() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Delete
        public HttpResponse delete() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Options
        public HttpResponse options() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Patch
        public HttpResponse patch() {
            return HttpResponse.of(HttpStatus.OK);
        }

        @Trace
        public HttpResponse trace() {
            return HttpResponse.of(HttpStatus.OK);
        }
    }
}
