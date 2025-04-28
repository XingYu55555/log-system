import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;

public class AnalysisService {
    private static final String BROKER_URL = "tcp://localhost:61616";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int N = 100; // 保留最近100条
    private static final int T = 5;    // 5秒分析间隔
    private static final int S = 10;   // 10秒内错误超50%告警
    
    private final Map<String, Deque<LogProducer.LogMessage>> deviceLogs = new ConcurrentHashMap<>();
    private final Map<String, String> lastErrors = new ConcurrentHashMap<>();

    public void start() throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic logTopic = session.createTopic("LOG_TOPIC");
        MessageConsumer consumer = session.createConsumer(logTopic);

        // 定时分析任务
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::analyzeLogs, T, T, TimeUnit.SECONDS);

        connection.start();
        consumer.setMessageListener(msg -> {
            try {
                TextMessage message = (TextMessage) msg;
                LogProducer.LogMessage log = mapper.readValue(message.getText(), LogProducer.LogMessage.class);
                
                // 维护日志队列
                Deque<LogProducer.LogMessage> logs = deviceLogs.computeIfAbsent(
                    log.device_id, 
                    k -> new LinkedList<>()
                );
                logs.addLast(log);
                if (logs.size() > N) logs.removeFirst();
                
                // 记录错误
                if ("ERROR".equals(log.log_level)) {
                    lastErrors.put(log.device_id, log.timestamp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void analyzeLogs() {
        deviceLogs.forEach((deviceId, logs) -> {
            int total = logs.size();
            long errors = logs.stream().filter(l -> "ERROR".equals(l.log_level)).count();
            long warns = logs.stream().filter(l -> "WARN".equals(l.log_level)).count();
            
            double errorRate = (double) errors / total;
            double warnRate = (double) warns / total;
            
            // 发布分析结果
            publishAnalysis(deviceId, errorRate, warnRate);
            
            // 触发告警
            if (errorRate > 0.5) {
                publishAlert(deviceId, errorRate);
            }
        });
    }

    private void publishAnalysis(String deviceId, double errorRate, double warnRate) {
        // 实现消息发送（参考后续步骤）
        try {
            ConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
            Connection conn = factory.createConnection();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createTopic("ANALYSIS_TOPIC"));

            String result = String.format(
                    "设备 %s - ERROR: %.2f%%, WARN: %.2f%%, 最后错误时间: %s",
                    deviceId, errorRate*100, warnRate*100, lastErrors.getOrDefault(deviceId, "无")
            );

            producer.send(session.createTextMessage(result));
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void publishAlert(String deviceId, double errorRate) {
        // 实现告警发送（参考后续步骤）
    }

    public static void main(String[] args) throws Exception {
        new AnalysisService().start();
    }
}