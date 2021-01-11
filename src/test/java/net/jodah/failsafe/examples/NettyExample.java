/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe.examples;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

public class NettyExample {
  static final String HOST = System.getProperty("host", "127.0.0.1");
  static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));
  static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));

  public static void main(String... args) throws Throwable {
    EventLoopGroup group = new NioEventLoopGroup();
    Bootstrap bootstrap = createBootstrap(group);
    RetryPolicy retryPolicy = new RetryPolicy().withDelay(1, TimeUnit.SECONDS);

    Failsafe.with(retryPolicy).with(group).runAsync(
        execution -> bootstrap.connect(HOST, PORT).addListener((ChannelFutureListener) channelFuture -> {
          if (channelFuture.isSuccess()) {
            System.out.println("Connected!");
            try {
              channelFuture.sync();
              channelFuture.channel().closeFuture().sync();
            } catch (Exception ignore) {
              group.shutdownGracefully();
            }
          } else if (!execution.retryOn(channelFuture.cause()))
            System.out.println("Connection attempts failed");
        }));

    Thread.sleep(5000);
  }

  static Bootstrap createBootstrap(EventLoopGroup group) {
    return new Bootstrap().group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          public void initChannel(SocketChannel ch) throws Exception {
            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ctx.write(msg);
              }

              @Override
              public void channelReadComplete(ChannelHandlerContext ctx) {
                ctx.flush();
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                // Close the connection when an exception is raised.
                cause.printStackTrace();
                ctx.close();
              }
            });
          }
        });
  }
}
