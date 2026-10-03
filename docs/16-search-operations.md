# Search operations

## Runtime search mode

Admin mở `/admin/search` và chọn một mode:

- `POSTGRES`: không gọi Elasticsearch; search dùng `tsvector + GIN`.
- `ELASTICSEARCH`: ưu tiên index; request lỗi vẫn fallback PostgreSQL.
- `AUTO`: ưu tiên Elasticsearch và fallback PostgreSQL khi cluster không khả dụng.

Mode được lưu trong PostgreSQL, có optimistic version và audit actor. Mặc định an toàn
là `POSTGRES`. `APP_SEARCH_ES_ENABLED` vẫn là capability flag lúc deploy; nếu capability
tắt thì UI không cho chọn Elasticsearch.

## Lifecycle trên host

Không mount `/var/run/docker.sock` vào app. Host operator cài
`scripts/es-control.sh` tại `/opt/kg/scripts/es-control.sh` và giới hạn quyền
qua sudoers/systemd. Script chỉ nhận ba action:

```bash
es-control.sh status
es-control.sh stop
es-control.sh start
```

Trước khi stop ES, chuyển runtime mode về `POSTGRES`. Stop chỉ dừng container, không xóa
volume `esdata`. Sau khi start, chờ healthcheck `yellow` hoặc `green` trước khi chọn lại
`ELASTICSEARCH`/`AUTO`.

## Rollback

Nếu ES lỗi:

1. Admin chọn `POSTGRES`.
2. Kiểm tra `/api/v1/search` vẫn trả kết quả.
3. Giữ ES stopped hoặc xem log/health trên host.
4. Khi cluster healthy, chọn `AUTO` để fallback vẫn còn hoạt động.

Search outbox được prune theo retention; không được xem nó là nguồn sự thật. Khi ES được
bật lại, reindex-on-startup có thể dựng lại index từ PostgreSQL.
