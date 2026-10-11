package com.thang.user.controller;
import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import org.springframework.http.*;import java.util.Map;
@RestControllerAdvice(assignableTypes={AccountFeaturesController.class,AccountRecoveryController.class,AuthOperationsController.class,AccountAuditExportController.class})
public class AccountFeatureErrors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> invalid(ResponseStatusException error){return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",error.getReason()==null?"Yêu cầu không hợp lệ":error.getReason()));}
 @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("message","Dữ liệu đã thay đổi; hãy tải lại"));}
 @ExceptionHandler(org.springframework.dao.DataAccessException.class) ResponseEntity<?> unavailable(){return ResponseEntity.status(503).body(Map.of("message","Dịch vụ tài khoản tạm gián đoạn; hãy thử lại"));}
}
