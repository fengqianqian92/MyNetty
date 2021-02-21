package com.qiafeng.systemio;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Socket;

public class MyNetty {
    @Test
    public void myByteBuf() {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(8, 20);
        print(buf);

        ByteBuf buf1 = UnpooledByteBufAllocator.DEFAULT.heapBuffer(8, 20);
        print(buf1);

        ByteBuf buf2 = PooledByteBufAllocator.DEFAULT.heapBuffer(8, 20);
        print(buf2);

        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
        buf.writeBytes(new byte[]{1,2,3,4});
        print(buf);
    }

    public void print(ByteBuf buf) {
        System.out.println("buf.isReadable()" + buf.isReadable());
        System.out.println("buf.readerIndex()" + buf.readerIndex());
        System.out.println("buf.readableBytes()" + buf.readableBytes());
        System.out.println("buf.isWritable()" + buf.isWritable());
        System.out.println("buf.writerIndex()" + buf.writerIndex());
        System.out.println("buf.writableBytes()" + buf.writableBytes());
        System.out.println("buf.capacity()" + buf.capacity());
        System.out.println("buf.maxCapacity()" + buf.maxCapacity());
        System.out.println("buf.isDirect()" + buf.isDirect());
        System.out.println("---------------");
    }

    /*
    Client
    1. send data
     */
    @Test
    public void loopExecutor() {
        // group thread pool
        NioEventLoopGroup selector = new NioEventLoopGroup(2);
        selector.execute(()->{
            System.out.println("Hello World001");
        });
        selector.execute(()->{
            System.out.println("Hello World002");
        });
    }

    @Test
    public void clientMode() throws InterruptedException {
        NioEventLoopGroup thread = new NioEventLoopGroup(1);

        NioSocketChannel client = new NioSocketChannel();
        thread.register(client);

        ChannelPipeline p = client.pipeline();
        p.addLast(new MyInHandler());

        ChannelFuture connect = client.connect(new InetSocketAddress("localhost",9090));
        ChannelFuture sync = connect.sync();

        ByteBuf buf = Unpooled.copiedBuffer("Hello server".getBytes());
        ChannelFuture send = client.writeAndFlush(buf);
        send.sync();

        sync.channel().closeFuture().sync();

    }

    @Test
    public void serverMode() {
        NioEventLoopGroup thread = new NioEventLoopGroup(1);
        NioServerSocketChannel server = new NioServerSocketChannel();

        thread.register(server);

        ChannelPipeline p = server.pipeline();
        p.addLast(new MyAcceptHandler(thread, new ChannelInit()));
        ChannelFuture bind = server.bind(new InetSocketAddress("localhost", 8080));

        try {
            bind.sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("server close");
    }

    @Test
    public void nettyClient() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        ChannelFuture connect = bs.group(group)
                .channel(NioSocketChannel.class)
//                .handler(new ChannelInit())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline p = socketChannel.pipeline();
                        p.addLast(new MyInHandler());
                    }
                })
                .connect("localhost", 9999);

        Channel client = connect.sync().channel();

        ByteBuf buf = Unpooled.copiedBuffer("Hello server".getBytes());
        ChannelFuture send = client.writeAndFlush(buf);
        send.sync();

        client.closeFuture().sync();
    }

    @Test
    public void nettyServer() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ServerBootstrap bs = new ServerBootstrap();
        ChannelFuture bind = bs.group(group, group)
                .channel(NioServerSocketChannel.class)
//                .childHandler(new ChannelInit())
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new MyInHandler());
                    }
                })
                .bind(new InetSocketAddress("localhost", 8888));
        bind.sync().channel().closeFuture().sync();
    }

}



/*
Very important
单例？？？
 */
@ChannelHandler.Sharable
class ChannelInit extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Channel client = ctx.channel();
        ChannelPipeline p = client.pipeline();
        p.addLast(new MyInHandler());//3.Add MyInHandler
        ctx.pipeline().remove(this);//4.remove current handler
    }
}

class MyInHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client registered");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client active");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf)msg;
//        CharSequence str = buf.readCharSequence(buf.readableBytes(), CharsetUtil.UTF_8);
        CharSequence str = buf.getCharSequence(0, buf.readableBytes(), CharsetUtil.UTF_8);
        System.out.println(str);
        ctx.writeAndFlush(buf);
    }
}

class MyAcceptHandler extends ChannelInboundHandlerAdapter {
    private final EventLoopGroup selector;
    private final ChannelHandler handler;

    public MyAcceptHandler(EventLoopGroup thread, ChannelHandler myInHandler) {
        this.selector = thread;
        this.handler = myInHandler;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("server registered");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //listen socket accpet client
        //socket        R/W
        SocketChannel client = (SocketChannel)msg; //accept by Netty
        //handler
        ChannelPipeline p = client.pipeline();
        p.addLast(handler);//1.ChannelInit
        //register
        selector.register(client); //2.channelRegistered of ChannelInit
    }
}
