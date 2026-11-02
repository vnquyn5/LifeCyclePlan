# AUTH-01 Retention Decision Record

**Project:** LifeCyclePlan — Trợ lý AI Lập kế hoạch Lối sống Cá nhân  
**WBS:** 1.1.10  
**Date:** 2026-11-02  
**Decision owner:** Project Owner  
**Decision status:** Development policy approved; production policy pending review.

## Decision

1. Không sử dụng dữ liệu cá nhân thật cho kiểm thử bảo mật trong môi trường development.
2. Email, password hash và trạng thái tài khoản được lưu trong thời gian tài khoản còn hoạt động, trừ khi có yêu cầu xóa hoặc quy định khác được xác định.
3. Không lưu plaintext password, confirm password, authentication token hoặc secret trong application logs.
4. Đặt mục tiêu lưu authentication security logs tối đa 90 ngày trên môi trường development. Cần cấu hình retention thực tế và kiểm chứng việc xóa log trước khi đánh dấu implemented.
5. Correlation ID được giữ cùng thời hạn với log chứa nó.
6. Rate-limit counters phải có thời hạn hết hạn và giới hạn sử dụng bộ nhớ; không giữ vô thời hạn.
7. Chính sách xóa/anonymization dữ liệu tài khoản và các ngoại lệ lưu giữ hợp pháp sẽ được thiết kế trong các task privacy/account phù hợp.
8. Retention của access token, refresh token và session được xác định trong AUTH-02.

## Outstanding controls

- Authentication log retention automation: **PENDING**.
- Account deletion/anonymization workflow: **DEFERRED**.
- Production legal/privacy review: **PENDING**.
- AUTH-02 token/session lifecycle: **BLOCKED BY DEPENDENCY**.

## Acceptance boundary

Quyết định này áp dụng cho development và ghi nhận rõ những kiểm soát chưa được thực thi. Nó không chứng nhận tuân thủ pháp lý hoặc sẵn sàng triển khai production.
