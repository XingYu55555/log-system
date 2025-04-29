import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;

public class MonitoringService {
    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageConsumer analysisConsumer = session.createConsumer(session.createTopic("ANALYSIS_TOPIC"));
        MessageConsumer alertConsumer = session.createConsumer(session.createTopic("ALERT_TOPIC"));

        analysisConsumer.setMessageListener(msg -> {
            try {
                System.out.println("\n[监控] 分析结果: " + ((TextMessage) msg).getText());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        alertConsumer.setMessageListener(msg -> {
            try {
                System.out.println("\n[!!! 严重告警 !!!] " + ((TextMessage) msg).getText());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        connection.start();
        System.out.println("监控服务已启动...");
        Thread.currentThread().join();
    }
}