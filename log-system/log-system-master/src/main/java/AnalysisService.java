import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AnalysisService {
    private static final String BROKER_URL = "tcp://localhost:61616";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int N = Integer.parseInt(System.getProperty("N", "100")); // 动态配置
    private static final int T = Integer.parseInt(System.getProperty("T", "5"));
    private static final int S = Integer.parseInt(System.getProperty("S", "10"));

    private static class LogEntry {
        String logLevel;
        long timestamp;
        LogEntry(String logLevel, long timestamp) {
            this.logLevel = logLevel;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, Deque<LogEntry>> deviceLogs = new ConcurrentHashMap<>();
    private final Map<String, String> lastErrors = new ConcurrentHashMap<>();
    private Connection conn;
    private Session session;
    private MessageProducer analysisProducer;
    private MessageProducer alertProducer;

    public void start() throws Exception {
        ConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        conn = factory.createConnection();
        session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        analysisProducer = session.createProducer(session.createTopic("ANALYSIS_TOPIC"));
        alertProducer = session.createProducer(session.createTopic("ALERT_TOPIC"));

        // 订阅日志
        MessageConsumer consumer = session.createConsumer(session.createTopic("LOG_TOPIC"));
        consumer.setMessageListener(msg -> processLogMessage((TextMessage) msg));

        // 定时任务
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::analyzeAndClean, T, T, TimeUnit.SECONDS);

        conn.start();
        System.out.println("分析服务已启动...");
    }

    private void processLogMessage(TextMessage message) {
        try {
            LogProducer.LogMessage log = mapper.readValue(message.getText(), LogProducer.LogMessage.class);
            Deque<LogEntry> logs = deviceLogs.computeIfAbsent(
                    log.device_id,
                    k -> new ConcurrentLinkedDeque<>()
            );
            logs.add(new LogEntry(log.log_level, System.currentTimeMillis()));
            if (logs.size() > N) logs.removeFirst();

            if ("ERROR".equals(log.log_level)) {
                lastErrors.put(log.device_id, log.timestamp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void analyzeAndClean() {
        long currentTime = System.currentTimeMillis();
        deviceLogs.forEach((deviceId, logs) -> {
            // 按时间窗口过滤
            List<LogEntry> recentLogs = logs.stream()
                    .filter(entry -> entry.timestamp >= currentTime - S * 1000L)
                    .collect(Collectors.toList());

            if (recentLogs.isEmpty()) return;

            long errors = recentLogs.stream().filter(e -> "ERROR".equals(e.logLevel)).count();
            double errorRate = (double) errors / recentLogs.size();

            // 发布分析结果
            publishAnalysis(deviceId, errorRate, recentLogs);

            // 触发告警
            if (errorRate > 0.5) {
                publishAlert(deviceId, errorRate);
            }
        });

        // 清理过期日志
        deviceLogs.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(log -> log.timestamp < currentTime - S * 1000L);
            return entry.getValue().isEmpty();
        });
    }

    private void publishAnalysis(String deviceId, double errorRate, List<LogEntry> logs) {
        long warns = logs.stream().filter(l -> "WARN".equals(l.logLevel)).count();
        double warnRate = (double) warns / logs.size();

        try {
            String result = String.format(
                    "设备 %s - ERROR: %.2f%%, WARN: %.2f%%, 最后错误时间: %s",
                    deviceId, errorRate * 100, warnRate * 100,
                    lastErrors.getOrDefault(deviceId, "无")
            );
            analysisProducer.send(session.createTextMessage(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void publishAlert(String deviceId, double errorRate) {
        try {
            String alertMsg = String.format(
                    "设备 %s 在最近 %d 秒内 ERROR 占比超过 50%%。当前占比：%.2f%%",
                    deviceId, S, errorRate * 100
            );
            alertProducer.send(session.createTextMessage(alertMsg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        new AnalysisService().start();
        Thread.currentThread().join();
    }
}