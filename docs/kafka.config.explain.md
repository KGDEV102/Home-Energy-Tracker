# Giải thích cấu hình Kafka trong Docker Compose

Kafka trong project được cấu hình theo mô hình **một node KRaft** để phục vụ môi trường local/test. Một container đồng thời giữ hai vai trò:

- **Broker**: nhận, lưu trữ và phân phối message.
- **Controller**: quản lý metadata và trạng thái cluster, thay thế ZooKeeper.

## Tổng quan các địa chỉ

| Mục đích | Địa chỉ | Đối tượng sử dụng |
| --- | --- | --- |
| Quản lý cluster | `kafka:9093` | Kafka controller |
| Kết nối nội bộ Docker | `kafka:9092` | Các application container |
| Kết nối từ máy host | `localhost:9094` | IDE, CLI và ứng dụng chạy ngoài Docker |

## Giải thích từng cấu hình

### `KAFKA_CONTROLLER_QUORUM_VOTERS`

```yaml
KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
```

Khai báo các controller có quyền biểu quyết và quản lý cluster:

- `1`: ID của controller.
- `kafka`: hostname của container.
- `9093`: cổng controller.

Có thể hiểu cấu hình này là: controller có ID `1` nằm tại địa chỉ `kafka:9093`.

### `KAFKA_CONTROLLER_LISTENER_NAMES`

```yaml
KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
```

Chỉ định listener tên `CONTROLLER` được dùng cho hoạt động quản lý cluster.

### `KAFKA_INTER_BROKER_LISTENER_NAME`

```yaml
KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
```

Chỉ định listener mà các broker sử dụng để giao tiếp với nhau. Project hiện chỉ có một broker nên cấu hình này chủ yếu để Kafka khởi tạo đúng; nó sẽ quan trọng hơn khi cluster có nhiều broker.

### `KAFKA_LISTENERS`

```yaml
KAFKA_LISTENERS: CONTROLLER://:9093,PLAINTEXT://:9092,PLAINTEXT_HOST://:9094
```

Khai báo những listener và cổng Kafka thực sự mở bên trong container:

| Listener | Cổng | Mục đích |
| --- | ---: | --- |
| `CONTROLLER` | `9093` | Giao tiếp và quản lý cluster |
| `PLAINTEXT` | `9092` | Các service bên trong Docker kết nối |
| `PLAINTEXT_HOST` | `9094` | Ứng dụng trên máy host kết nối |

### `KAFKA_ADVERTISED_LISTENERS`

```yaml
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:${KAFKA_EXTERNAL_PORT:-9094}
```

Khai báo địa chỉ mà Kafka thông báo lại cho client sau lần kết nối đầu tiên:

- Container khác trong Docker dùng `kafka:9092`.
- Ứng dụng chạy trên máy host dùng `localhost:9094`.
- Nếu `KAFKA_EXTERNAL_PORT` có trong `.env`, Kafka sử dụng giá trị đó thay cho `9094`.

Ví dụ:

```env
KAFKA_EXTERNAL_PORT=19094
```

Khi đó ứng dụng trên máy host kết nối tới `localhost:19094`.

> `KAFKA_LISTENERS` là nơi Kafka lắng nghe bên trong container; `KAFKA_ADVERTISED_LISTENERS` là địa chỉ Kafka trả về để client biết phải kết nối tới đâu.

### `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`

```yaml
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
```

Ánh xạ mỗi listener với giao thức bảo mật. Tất cả listener hiện dùng `PLAINTEXT`, nghĩa là không có TLS và không yêu cầu username/password.

Cấu hình này phù hợp với local/test, nhưng không nên public trực tiếp trong production.

### `KAFKA_AUTO_CREATE_TOPICS_ENABLE`

```yaml
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
```

Không cho Kafka tự động tạo topic khi ứng dụng gửi message đến một topic chưa tồn tại. Các topic được tạo chủ động bởi container `kafka-init`, giúp tránh tạo nhầm topic do sai tên.

### `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR`

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

Kafka lưu vị trí đã đọc của consumer group trong topic nội bộ `__consumer_offsets`. Giá trị `1` nghĩa là dữ liệu offsets chỉ có một bản, phù hợp vì môi trường local chỉ có một broker.

### `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR`

```yaml
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
```

Quy định số replica tối thiểu phải đang hoạt động để Kafka chấp nhận transaction. Giá trị là `1` vì cluster local chỉ có một broker.

### `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR`

```yaml
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
```

Quy định số bản sao của dữ liệu trạng thái transaction. Local dùng `1`; production với nhiều broker thường sử dụng replication factor lớn hơn, chẳng hạn `3`.

### `KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS`

```yaml
KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
```

Khi một consumer group mới xuất hiện, Kafka phân chia partition ngay mà không chờ thêm. Điều này giúp các consumer khởi động nhanh hơn trong local/test.

### `KAFKA_LOG_DIRS`

```yaml
KAFKA_LOG_DIRS: /tmp/kraft-combined-logs
```

Thư mục bên trong container dùng để lưu message, dữ liệu partition và metadata KRaft. Thư mục này được gắn với Docker volume để dữ liệu không mất khi container được tạo lại.

### `CLUSTER_ID`

```yaml
CLUSTER_ID: 4L6g3nShT-eMCtK--X86sw
```

ID duy nhất của Kafka cluster KRaft. Không nên thay đổi ID này khi volume `kafka-data` vẫn chứa dữ liệu cũ, vì metadata trong volume đã gắn với cluster ID hiện tại.

## Port mapping

```yaml
ports:
  - "${KAFKA_EXTERNAL_PORT:-9094}:9094"
```

Map cổng trên máy host vào cổng `9094` của container:

```text
KAFKA_EXTERNAL_PORT hoặc 9094 trên host -> cổng 9094 trong container
```

Port mapping chỉ dành cho ứng dụng chạy ngoài Docker. Các container trong cùng Compose network kết nối trực tiếp bằng `kafka:9092`.

## Lưu dữ liệu bằng volume

```yaml
volumes:
  - kafka-data:/tmp/kraft-combined-logs
```

Docker volume `kafka-data` lưu dữ liệu Kafka bên ngoài vòng đời container. Việc restart hoặc tạo lại container không làm mất dữ liệu, miễn là volume chưa bị xóa.

## Khởi tạo topic bằng `kafka-init`

Container `kafka-init` chờ Kafka healthy rồi tạo hai topic:

| Topic | Mục đích |
| --- | --- |
| `energy-usage.v1` | Chứa sự kiện sử dụng điện năng |
| `energy-usage.v1-dlt` | Chứa message không xử lý được sau retry |

Mỗi topic có:

```text
3 partitions
replication factor = 1
```

Ba partition cho phép consumer xử lý song song. Replication factor bằng `1` vì cluster local chỉ có một broker. Sau khi tạo topic thành công, container `kafka-init` kết thúc với trạng thái `Exited (0)`; đây là hành vi bình thường.

## Cách application service kết nối

Trong Docker Compose, các service nhận địa chỉ:

```yaml
KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

Trong Spring Boot, cấu hình thường có dạng:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}
```

Do đó:

- Chạy bằng Docker Compose: dùng `kafka:9092`.
- Chạy service trực tiếp từ IDE: mặc định dùng `localhost:9094`.

## Luồng hoạt động trong project

```text
Kafka khởi động
  -> healthcheck thành công
  -> kafka-init tạo topic
  -> ingestion-service gửi EnergyUsageEvent vào energy-usage.v1
  -> usage-service nhận message và ghi dữ liệu vào InfluxDB
  -> message lỗi sau khi hết retry được gửi vào energy-usage.v1-dlt
```

## Lưu ý khi dùng production

Cấu hình hiện tại được tối ưu cho local/test. Khi triển khai production cần cân nhắc:

- Nhiều broker/controller để tránh single point of failure.
- TLS và SASL authentication thay cho `PLAINTEXT`.
- Replication factor lớn hơn `1`.
- Chính sách retention và giới hạn dung lượng.
- Monitoring, alerting và backup.
- Kafka managed service hoặc Kafka cluster riêng thay cho container một node.
