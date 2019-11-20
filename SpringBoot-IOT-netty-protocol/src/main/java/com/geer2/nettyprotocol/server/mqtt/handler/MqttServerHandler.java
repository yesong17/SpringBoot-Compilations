/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.geer2.nettyprotocol.server.mqtt.handler;

import com.geer2.nettyprotocol.server.bean.MqttChannel;
import com.geer2.nettyprotocol.server.bean.forstb.StbReportMsg;
import com.geer2.nettyprotocol.server.mqtt.api.ChannelService;
import com.geer2.nettyprotocol.server.mqtt.api.MqttHandlerIntf;
import com.geer2.nettyprotocol.server.mqtt.api.AbstractMqttHandlerService;
import com.geer2.nettyprotocol.util.json.gson.GsonJsonUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.Date;


/**
 * @description mqtt消息处理实现类
 * @author JiaweiWu
 * @date 2019-11-12
 */
@Sharable
@Component
public class MqttServerHandler extends ChannelInboundHandlerAdapter {

    @Autowired
    MqttHandlerIntf mqttHandlerIntf;

    private static MqttServerHandler mqttServerHandler;
    @PostConstruct
    public void init(){
        mqttServerHandler = this;
    }

    @Autowired
    private ChannelService channelService;
    
    public static Logger log = LogManager.getLogger(MqttServerHandler.class);

    // 所有该上报的消息集合   mac+plan
    //    public static Map<Integer,Map<String, UpMessage>> upMap=new ConcurrentHashMap<Integer,Map<String, UpMessage>>();

    public MqttServerHandler() {

    }

    /**
     * 连接成功后调用的方法
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        // Send greeting for a new connection
        System.out.println("Welcome to " + InetAddress.getLocalHost().getHostName());
        ctx.writeAndFlush("Welcome to " + InetAddress.getLocalHost().getHostName() + "!\r\n");
        ctx.writeAndFlush("It is " + new Date() + " now.\r\n");
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof MqttMessage) {
            processMqttMsg(ctx, msg);
        } else {
            ctx.close();
        }
    }

    public void processMqttMsg(ChannelHandlerContext ctx, Object request) {
        try {
            //处理mqtt消息
            if (((MqttMessage)request).decoderResult().isSuccess()) {
                Channel channel = ctx.channel();
                AbstractMqttHandlerService abstractMqttHandlerService = (AbstractMqttHandlerService) mqttServerHandler.mqttHandlerIntf;
                MqttMessage req = (MqttMessage)request;
                //处理登陆连接消息
                MqttFixedHeader mqttFixedHeader = req.fixedHeader();
                if(mqttFixedHeader.messageType().equals(MqttMessageType.CONNECT)){
                    if(!abstractMqttHandlerService.login(channel, (MqttConnectMessage) request)){
                        channel.close();
                    }
                    return ;
                }
                MqttChannel mqttChannel = mqttServerHandler.channelService.getMqttChannel(mqttServerHandler.channelService.getDeviceId(channel));
                if(mqttChannel!=null && mqttChannel.isLogin()) {
                    switch (req.fixedHeader().messageType()) {
                        case SUBSCRIBE:
                            abstractMqttHandlerService.subscribe(channel, (MqttSubscribeMessage) request);
                            return;
                        case PUBLISH:
                            abstractMqttHandlerService.publish(channel, (MqttPublishMessage) request);
                            return;
                        case PINGREQ:
                            doPingreoMessage(ctx, request);
                            return;
                        case PUBACK:
                            doPubAck(ctx, request);
                            return;
                        case PUBREC:
                        case PUBREL:
                        case PUBCOMP:
                        case UNSUBACK:
                            return;
                        case PINGRESP:
                            doPingrespMessage(ctx, request);
                            return;
                        case DISCONNECT:
                            abstractMqttHandlerService.disconnect(channel);
                            return;
                        default:
                            return;
                    }
                }
            }
        }
        catch (Exception ex) {

        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception{
        log.info("【DefaultMqttHandler：channelInactive】"+ctx.channel().localAddress().toString()+"关闭成功");
        mqttServerHandler.mqttHandlerIntf.close(ctx.channel());
//        super.channelInactive(ctx);
        //清理设备缓存
//        if (ctx.channel().hasAttr(DeviceManage.DEVICE)) {
//            String device = ctx.channel().attr(DeviceManage.DEVICE).get();
//            DeviceManage.DEVICE_MAP.remove(device);
//            DeviceManage.mqttChannels.remove(device);
//            DeviceManage.DEVICE_ONLINE_MAP.remove(device);
//        }
    }

//    @Override
//    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception{
//        //这里执行客户端断开连接后的操作
//        log.info("handlerRemove:");
//        mqttServerHandler.mqttHandlerIntf.close(ctx.channel());
//
//
//    }
    /**
     * 超时处理
     * 服务器端 设置超时 ALL_IDLE  <  READER_IDLE ， ALL_IDLE 触发时发送心跳，客户端需响应，
     * 如果客户端没有响应 说明 掉线了 ，然后触发 READER_IDLE ，
     * READER_IDLE 里 关闭链接
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof IdleStateEvent){
            mqttServerHandler.mqttHandlerIntf.doTimeOutEvt(ctx.channel(),(IdleStateEvent)evt);
        }
        super.userEventTriggered(ctx, evt);
//        if (evt instanceof IdleStateEvent)
//        {
//            IdleStateEvent event = (IdleStateEvent)evt;
//            if (event.state().equals(IdleState.READER_IDLE))
//            {
//            	if (ctx.channel().hasAttr(DeviceManage.DEVICE)){
//            		String device = ctx.channel().attr(DeviceManage.DEVICE).get();
//                    //+ctx);
//            		 log.debug("ctx heartbeat timeout,close!"+device);
//                    //+ctx);
//            		 log.debug("ctx heartbeat timeout,close!");
//                     if(DeviceManage.UN_CONNECT_MAP.containsKey(device))
//                     {
//                         DeviceManage.UN_CONNECT_MAP.put(device, DeviceManage.UN_CONNECT_MAP.get(device)+1);
//                     }else
//                     {
//                         DeviceManage.UN_CONNECT_MAP.put(device, new Long(1));
//                     }
//            	}
//
//                ctx.fireChannelInactive();
//                ctx.close();
//            }else if(event.state().equals(IdleState.ALL_IDLE))
//            {
//            	log.debug("发送心跳给客户端！");
//            	buildHearBeat(ctx);
//            }
//        }
//        super.userEventTriggered(ctx, evt);
    }

    /**
     * 心跳响应
     * @param ctx
     * @param request
     */
    private void doPingreoMessage(ChannelHandlerContext ctx, Object request) {
        System.out.println("响应心跳！");
        MqttChannel mqttChannel = mqttServerHandler.channelService.getMqttChannel(
                mqttServerHandler.channelService.getDeviceId(ctx.channel()));
        mqttChannel.setReaderNum(0);
        MqttFixedHeader header = new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessage pingespMessage = new MqttMessage(header);
        ctx.writeAndFlush(pingespMessage);
    }

    /**
     * 收到心跳
     * @param ctx
     * @param request
     */
    private void doPingrespMessage(ChannelHandlerContext ctx, Object request) {
        System.out.println("收到心跳请求！");
    }
    /**
     * 处理连接请求
     * @param ctx
     * @param request
     */
//    private void doConnectMessage(ChannelHandlerContext ctx, Object request)
//    {
//        MqttConnectMessage message = (MqttConnectMessage)request;
//        MqttConnAckVariableHeader variableheader = new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, false);
//        MqttConnAckMessage connAckMessage = new MqttConnAckMessage(Constants.CONNACK_HEADER, variableheader);
//        ctx.write(connAckMessage);
//        //String user = message.variableHeader().name();
//        String stb_code = message.payload().clientIdentifier();
//        log.debug("connect ,stb_code is :" + stb_code);
//        //将设备信息写入变量
//        if (!ctx.channel().hasAttr(DeviceManage.DEVICE))
//        {
//            ctx.channel().attr(DeviceManage.DEVICE).set(stb_code);
//        }
//        //将连接信息写入缓存
//        DeviceManage.DEVICE_MAP.put(stb_code, ctx);
//        MqttChannel mqttChannel= MqttChannel.builder().channel(ctx.channel()).build();
//        DeviceManage.mqttChannels.put(stb_code,mqttChannel);
//        DeviceManage.DEVICE_ONLINE_MAP.put(stb_code, DateUtil.getCurrentTimeStr());
////        log.debug("the user num is " + userMap.size());
//
//        /**
//         * 上线时，处理离线消息
//         */
//        for (String key : HttpServerHandler.OffLineUserMsgMap.keySet())
//        {
//            if (HttpServerHandler.OffLineUserMsgMap.get(key).contains(stb_code))
//            {
//                MsgToNode msg = HttpServerHandler.messageMap.get(key);
//                SendOfflineMessageThread t = new SendOfflineMessageThread(msg, stb_code);
//                HttpServerHandler.scheduledExecutorService.execute(t);
//            }
//        }
//    }

    /**
     * 处理客户端回执消息
     * @param ctx
     * @param request
     */
    private void doPubAck(ChannelHandlerContext ctx, Object request) {
        MqttPubAckMessage message = (MqttPubAckMessage)request;
//        log.debug(request);
        /* String user = ctx.channel().attr(USER).get();
         Map<String, UpMessage> requestMap=upMap.get(message.variableHeader().messageId());
         if(requestMap!=null&&requestMap.size()>0)
         {
             UpMessage upmessage=requestMap.get(user);
             if(upmessage!=null)
             {
                 upmessage.setStatus(Constants.SENDSUCESS);
                 requestMap.put(user, upmessage);
                 upMap.put(message.variableHeader().messageId(), requestMap);
             }
         }*/
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx)
    {
        ctx.flush();
    }


    /**
     * 连接异常断开连接
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("exception",cause);
        mqttServerHandler.mqttHandlerIntf.close(ctx.channel());
    }



    public static void main(String[] args)
    {
//        String msg = "{\"deviceNum\":\"88888888\",\"jumpFlag\":0,\"msgId\":\"M20170829153611748025\",\"status\":1,\"msgType\":6}";
    	 String msg = "{\"deviceNum\":\"88888888\",\"jumpFlag\":0,\"msgId\":\"M20170829153611748025\",\"status\":1}";
    	StbReportMsg stbmsg= GsonJsonUtil.fromJson(msg, StbReportMsg.class);
    	System.out.println(stbmsg.getMsgType());
    }
}
