package com.nhnacademy.order_server.controller.docs;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name ="Admin Order API", description = "관리자용 주문/반품/정책 관리 API")
@RequestMapping("api/admin")
public interface AdminOrderControllerDocs {
}
