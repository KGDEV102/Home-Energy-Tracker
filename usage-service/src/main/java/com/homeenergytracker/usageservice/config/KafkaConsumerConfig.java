package com.homeenergytracker.usageservice.config;

import com.homeenergytracker.usageservice.exception.InvalidEnergyUsageEventException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    /*
     * ProducerFactory này KHÔNG dùng để gửi energy event bình thường.
     *
     * Nó chỉ được sử dụng khi consumer xử lý message thất bại
     * và cần gửi message lỗi sang Dead Letter Topic (DLT).
     *
     * Phải hỗ trợ nhiều kiểu dữ liệu:
     *
     * Key:
     * - String: deviceUid bình thường.
     * - byte[]: key gốc nếu deserialize thất bại.
     *
     * Value:
     * - EnergyUsageEvent: event deserialize được nhưng xử lý thất bại.
     * - byte[]: nội dung JSON bị lỗi, không deserialize được.
     */
    @Bean
    public ProducerFactory<Object, Object> dltProducerFactory(
            KafkaProperties kafkaProperties
    ) {
        /*
         * Danh sách serializer dành cho Kafka key.
         *
         * LinkedHashMap giúp giữ thứ tự khai báo serializer.
         */
        Map<Class<?>, Serializer<?>> keySerializers =
                new LinkedHashMap<>();

        /*
         * Nếu key deserialize thất bại, Spring có thể giữ lại
         * key gốc dưới dạng byte[].
         */
        keySerializers.put(
                byte[].class,
                new ByteArraySerializer()
        );

        /*
         * Trường hợp bình thường, key là deviceUid dạng String.
         */
        keySerializers.put(
                String.class,
                new StringSerializer()
        );

        /*
         * Serializer dùng để chuyển Java object thành JSON
         * trước khi gửi event sang DLT.
         */
        JacksonJsonSerializer<Object> jsonSerializer =
                new JacksonJsonSerializer<>();

        /*
         * Không ghi tên Java class vào Kafka header.
         *
         * Ví dụ tránh tạo header phụ thuộc vào:
         * com.homeenergytracker.common.event.EnergyUsageEvent
         *
         * Nhờ đó message ít phụ thuộc vào package Java của service.
         */
        jsonSerializer.setAddTypeInfo(false);

        /*
         * Danh sách serializer dành cho Kafka value.
         */
        Map<Class<?>, Serializer<?>> valueSerializers =
                new LinkedHashMap<>();

        /*
         * Nếu JSON đầu vào bị hỏng và không tạo được EnergyUsageEvent,
         * Spring giữ dữ liệu gốc dưới dạng byte[].
         *
         * ByteArraySerializer sẽ gửi nguyên dữ liệu lỗi đó sang DLT
         * để chúng ta có thể kiểm tra lại sau.
         */
        valueSerializers.put(
                byte[].class,
                new ByteArraySerializer()
        );

        /*
         * Nếu event đã deserialize thành Java object nhưng xử lý lỗi,
         * ví dụ InfluxDB không kết nối được,
         * JacksonJsonSerializer sẽ chuyển object thành JSON để gửi DLT.
         */
        valueSerializers.put(
                Object.class,
                jsonSerializer
        );

        /*
         * Tạo Kafka producer dùng riêng cho DLT.
         *
         * buildProducerProperties():
         * lấy các cấu hình kết nối từ spring.kafka trong application.yml.
         *
         * DelegatingByTypeSerializer:
         * tự chọn serializer dựa trên kiểu dữ liệu thực tế.
         *
         * Tham số true:
         * cho phép tìm serializer dựa trên quan hệ kế thừa.
         * Ví dụ EnergyUsageEvent cũng là một Object.
         */
        return new DefaultKafkaProducerFactory<>(
                kafkaProperties.buildProducerProperties(),

                new DelegatingByTypeSerializer(
                        keySerializers,
                        true
                ),

                new DelegatingByTypeSerializer(
                        valueSerializers,
                        true
                )
        );
    }

    /*
     * KafkaTemplate là công cụ dùng để gửi message sang Kafka.
     *
     * Trong trường hợp này, DeadLetterPublishingRecoverer
     * sẽ sử dụng template này để gửi message lỗi sang DLT.
     */
    @Bean
    public KafkaTemplate<Object, Object> dltKafkaTemplate(
            /*
             * @Qualifier chỉ rõ rằng KafkaTemplate này phải sử dụng
             * đúng ProducerFactory dành cho DLT ở phía trên.
             */
            @Qualifier("dltProducerFactory")
            ProducerFactory<Object, Object> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    /*
     * CommonErrorHandler quy định cách xử lý khi method
     * được đánh dấu @KafkaListener ném ra exception.
     *
     * Spring Boot tự động tìm bean này và gắn nó vào
     * Kafka listener container.
     *
     * Chúng ta không cần tự gọi method kafkaErrorHandler().
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            @Qualifier("dltKafkaTemplate")
            KafkaTemplate<Object, Object> kafkaTemplate,

            // Topic chứa các message xử lý thất bại.
            @Value("${usage.kafka.dlt-topic}")
            String dltTopic,

            // Số lần thử lại sau lần xử lý đầu tiên.
            @Value("${usage.kafka.retry-attempts}")
            long retryAttempts,

            // Thời gian chờ giữa hai lần retry, đơn vị millisecond.
            @Value("${usage.kafka.retry-interval-ms}")
            long retryIntervalMs
    ) {
        /*
         * Recoverer chịu trách nhiệm gửi message sang DLT
         * sau khi đã hết số lần retry.
         */
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,

                        /*
                         * Message lỗi sẽ được đưa vào DLT
                         * với cùng partition của message gốc.
                         *
                         * Ví dụ:
                         * energy-usage.v1 partition 2
                         *     -> energy-usage.v1-dlt partition 2
                         *
                         * Vì vậy DLT cần có số partition bằng
                         * hoặc lớn hơn topic chính.
                         */
                        (record, exception) ->
                                new TopicPartition(
                                        dltTopic,
                                        record.partition()
                                )
                );

        /*
         * Nếu việc gửi message sang DLT cũng thất bại,
         * recoverer phải ném exception.
         *
         * Nhờ đó Kafka không xem message gốc là đã xử lý xong
         * và không commit offset nhầm.
         */
        recoverer.setFailIfSendResultIsError(true);

        /*
         * Quy định thời gian chờ và số lần retry.
         *
         * Ví dụ:
         * retryIntervalMs = 1000
         * retryAttempts   = 2
         *
         * Luồng chạy:
         * - Xử lý lần đầu.
         * - Lỗi -> chờ 1 giây -> retry lần 1.
         * - Lỗi -> chờ 1 giây -> retry lần 2.
         * - Vẫn lỗi -> gửi DLT.
         *
         * Tổng cộng có tối đa 3 lần xử lý:
         * 1 lần đầu + 2 lần retry.
         */
        FixedBackOff fixedBackOff = new FixedBackOff(
                retryIntervalMs,
                retryAttempts
        );

        /*
         * DefaultErrorHandler kết hợp:
         *
         * - fixedBackOff: retry như thế nào.
         * - recoverer: hết retry thì đưa message đi đâu.
         */
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        fixedBackOff
                );

        /*
         * InvalidEnergyUsageEventException là lỗi dữ liệu nghiệp vụ.
         *
         * Ví dụ:
         * - averagePowerW âm.
         * - eventId bị thiếu.
         * - Kafka key không khớp deviceUid.
         *
         * Retry không thể làm event sai trở thành đúng,
         * vì vậy không cần retry mà gửi thẳng sang DLT.
         */
        errorHandler.addNotRetryableExceptions(
                InvalidEnergyUsageEventException.class
        );

        return errorHandler;
    }
}
