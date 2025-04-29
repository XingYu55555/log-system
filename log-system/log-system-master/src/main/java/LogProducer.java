import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class LogProducer {
    private static final String BROKER_URL = "tcp://localhost:61616";
    private static final String TOPIC_NAME = "LOG_TOPIC";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final String deviceId;

    public LogProducer(String deviceId) {
        this.deviceId = deviceId;
    }

    public void start() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(TOPIC_NAME);
        MessageProducer producer = session.createProducer(topic);
        connection.start();

        String[] levels = {"INFO", "WARN", "ERROR"};
        Random random = new Random();

        while (true) {
            try {
                // 生成带格式化的时间戳
                LogMessage log = new LogMessage(
                        deviceId,
                        LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                        levels[random.nextInt(3)],
                        "System status"
                );

                TextMessage message = session.createTextMessage(mapper.writeValueAsString(log));
                producer.send(message);
                System.out.println("["+deviceId+"] 发送日志: " + log);

                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class LogMessage {
        public String device_id;
        public String timestamp;
        public String log_level;
        public String message;

        public LogMessage() {}

        public LogMessage(String deviceId, String timestamp, String level, String msg) {
            this.device_id = deviceId;
            this.timestamp = timestamp;
            this.log_level = level;
            this.message = msg;
        }

        @Override
        public String toString() {
            return String.format("%s [%s] %s", timestamp, log_level, message);
        }
    }

    public static void main(String[] args) throws JMSException {
        if (args.length < 1) {
            System.err.println("请指定设备ID，例如: java LogProducer device1");
            return;
        }
        new LogProducer(args[0]).start();
    }
}