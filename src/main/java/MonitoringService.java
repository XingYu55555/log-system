import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;

public class MonitoringService {
    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        // 订阅分析结果
        MessageConsumer analysisConsumer = session.createConsumer(
            session.createTopic("ANALYSIS_TOPIC")
        );
        
        // 订阅告警
        MessageConsumer alertConsumer = session.createConsumer(
            session.createTopic("ALERT_TOPIC")
        );

        analysisConsumer.setMessageListener(msg -> {
            try {
                TextMessage message = (TextMessage) msg;
                System.out.println("\n[监控] 分析结果: " + message.getText());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        alertConsumer.setMessageListener(msg -> {
            try {
                TextMessage message = (TextMessage) msg;
                System.out.println("\n[!!! 严重告警 !!!] " + message.getText());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        connection.start();
        System.out.println("监控服务已启动...");
        Thread.currentThread().join(); // 保持运行
    }
}