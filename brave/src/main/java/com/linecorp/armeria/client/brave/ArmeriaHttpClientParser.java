/*
 * Copyright 2019 LINE Corporation
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

package com.linecorp.armeria.client.brave;

import java.net.SocketAddress;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.RpcRequest;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.internal.brave.SpanTags;

import brave.SpanCustomizer;
import brave.http.HttpAdapter;
import brave.http.HttpClientParser;

/**
 * Default implementation of {@link HttpClientParser}.
 * This parser adds some custom tags and overwrites the name of span if {@link RequestLog#requestContent()}
 * is {@link RpcRequest}.
 * The following tags become available:
 * <ul>
 *   <li>http.url</li>
 *   <li>http.host</li>
 *   <li>http.protocol</li>
 *   <li>http.serfmt</li>
 *   <li>address.remote</li>
 *   <li>address.local</li>
 * </ul>
 */
final class ArmeriaHttpClientParser extends HttpClientParser {

    private static final ArmeriaHttpClientParser INSTANCE = new ArmeriaHttpClientParser();

    static ArmeriaHttpClientParser get() {
        return INSTANCE;
    }

    private ArmeriaHttpClientParser() {
    }

    @Override
    public <T> void response(HttpAdapter<?, T> rawAdapter, T res, Throwable error, SpanCustomizer customizer) {
        super.response(rawAdapter, res, error, customizer);
        if (!(res instanceof ClientRequestContext)) {
            return;
        }
        final ClientRequestContext ctx = (ClientRequestContext) res;

        final RequestLog requestLog = ctx.log();
        customizer.tag(SpanTags.TAG_HTTP_HOST, ClientRequestContextAdapter.authority(requestLog))
                  .tag(SpanTags.TAG_HTTP_URL, SpanTags.generateUrl(requestLog))
                  .tag(SpanTags.TAG_HTTP_PROTOCOL, ClientRequestContextAdapter.protocol(requestLog));

        final String serFmt = ClientRequestContextAdapter.serializationFormat(requestLog);
        if (serFmt != null) {
            customizer.tag(SpanTags.TAG_HTTP_SERIALIZATION_FORMAT, serFmt);
        }

        final SocketAddress raddr = ctx.remoteAddress();
        if (raddr != null) {
            customizer.tag(SpanTags.TAG_ADDRESS_REMOTE, raddr.toString());
        }

        final SocketAddress laddr = ctx.localAddress();
        if (laddr != null) {
            customizer.tag(SpanTags.TAG_ADDRESS_LOCAL, laddr.toString());
        }

        final String rpcMethod = ClientRequestContextAdapter.rpcMethod(requestLog);
        if (rpcMethod != null) {
            customizer.name(rpcMethod);
        }
    }
}
