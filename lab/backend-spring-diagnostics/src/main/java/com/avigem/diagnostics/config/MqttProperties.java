package com.avigem.diagnostics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "diagnostics.mqtt")
public class MqttProperties {
    private boolean enabled;
    private String brokerUri = "tcp://127.0.0.1:1883";
    private String clientId = "avigem-diagnostics";
    private String topic = "avigem/devices/+/imu";
    private String username = "";
    private String password = "";
    private int qos = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBrokerUri() { return brokerUri; }
    public void setBrokerUri(String brokerUri) { this.brokerUri = brokerUri; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }
}
