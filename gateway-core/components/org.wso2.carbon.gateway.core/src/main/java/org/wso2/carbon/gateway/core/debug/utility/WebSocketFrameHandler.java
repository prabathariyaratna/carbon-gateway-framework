/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.gateway.core.debug.utility;

import java.util.concurrent.Semaphore;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.gateway.core.debug.GatewayDebugManager;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketFrameHandler.class);
    private GatewayDebugManager gatewayDebugManager;
    private ChannelPromise handshakeFuture;
    private Semaphore runtimeSuspensionSem;

    public WebSocketFrameHandler(GatewayDebugManager gatewayDebugManager, Semaphore runtimeSuspensionSem) {
        this.gatewayDebugManager = gatewayDebugManager;
        this.runtimeSuspensionSem = runtimeSuspensionSem;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if ((msg instanceof TextWebSocketFrame)) {
                String text = ((TextWebSocketFrame) msg).text();
                gatewayDebugManager.processDebugCommand(text);
                return;
            }
        } catch (Exception e) {
            log.error("Exception occurred while injecting websocket frames to the engine", e);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        runtimeSuspensionSem.release();
        SharedChannelGroup.getInstance().add(ctx.channel());
    }
}
