package com.geer2.nettyMqtt.server;

import com.geer2.nettyMqtt.properties.InitBean;

/**
 * 启动类接口
 *
 * @author
 **/
public interface BootstrapServer {

    void shutdown();

    void setServerBean(InitBean serverBean);

    void start();


}
