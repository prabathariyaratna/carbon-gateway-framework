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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.group.ChannelGroup;

import java.util.Collection;
import java.util.Iterator;

import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelMatcher;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

public class SharedChannelGroup implements ChannelGroup, ChannelFutureListener {
    private static volatile SharedChannelGroup instance = null;
    private static final Object lock = new Object();
    private ChannelGroup channelGroup = new DefaultChannelGroup("Channel Group", GlobalEventExecutor.INSTANCE);


    /**
     * Retrieves the SharedChannelGroup singleton instance
     *
     * @return the SharedChannelGroup singleton instance
     */
    public static SharedChannelGroup getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new SharedChannelGroup();
                }
            }
        }
        return instance;
    }

    private SharedChannelGroup() {

    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public ChannelGroupFuture write(Object message) {
        return channelGroup.writeAndFlush(message);
    }

    @Override
    public ChannelGroupFuture write(Object o, ChannelMatcher channelMatcher) {
        return null;
    }

    @Override
    public ChannelGroup flush() {
        return null;
    }

    @Override
    public ChannelGroup flush(ChannelMatcher channelMatcher) {
        return null;
    }

    @Override
    public ChannelGroupFuture writeAndFlush(Object message) {
        return channelGroup.writeAndFlush(new TextWebSocketFrame(message.toString()));
    }

    @Override
    public ChannelGroupFuture flushAndWrite(Object o) {
        return null;
    }

    @Override
    public ChannelGroupFuture writeAndFlush(Object o, ChannelMatcher channelMatcher) {
        return null;
    }

    @Override
    public ChannelGroupFuture flushAndWrite(Object o, ChannelMatcher channelMatcher) {
        return null;
    }

    @Override
    public ChannelGroupFuture disconnect() {
        return null;
    }

    @Override
    public ChannelGroupFuture disconnect(ChannelMatcher channelMatcher) {
        return null;
    }

    @Override
    public ChannelGroupFuture close() {
        return null;
    }

    @Override
    public ChannelGroupFuture close(ChannelMatcher channelMatcher) {
        return null;
    }

    @Override
    public ChannelGroupFuture deregister() {
        return null;
    }

    @Override
    public ChannelGroupFuture deregister(ChannelMatcher channelMatcher) {
        return null;
    }

    @Override
    public int compareTo(ChannelGroup o) {
        return 0;
    }

    @Override
    public void operationComplete(ChannelFuture channelFuture) throws Exception {

    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public Iterator<Channel> iterator() {
        return null;
    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }

    @Override
    public boolean add(Channel channel) {
        return channelGroup.add(channel);
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends Channel> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {

    }
}
