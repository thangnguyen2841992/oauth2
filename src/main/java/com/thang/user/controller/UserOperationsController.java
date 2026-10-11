package com.thang.user.controller;
import com.thang.user.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.time.LocalDateTime;
@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
public class UserOperationsController {
 private final IUserRepository users;private final AdminAccountAuditRepository audit;
 public record Member(String userId,String name,String role) {}
 public record Learner(String userId,String name,String email) {}
 public record Entry(Long id,String actor,String action,String target,String detail,LocalDateTime createdAt) {}
 public record PageView<T>(List<T> content,long totalElements,int number,int totalPages) {}
 @GetMapping("/staff/learners/search") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
 public PageView<Learner> search(@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){if(page<0||search.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bộ lọc không hợp lệ");var result=users.learnerSearch(search.trim(),PageRequest.of(page,20)).map(u->new Learner(u.getUserId(),((u.getFirstName()==null?"":u.getFirstName())+" "+(u.getLastName()==null?"":u.getLastName())).trim(),u.getEmail()));return new PageView<>(result.getContent(),result.getTotalElements(),result.getNumber(),result.getTotalPages());}
 @GetMapping("/staff/learner-members") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
 public List<Learner> members(@RequestParam List<String> ids){if(ids.isEmpty()||ids.size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tối đa 100 học viên");return users.findAllById(ids).stream().filter(u->u.isActive()&&"USER".equals(u.getRoleName())).map(u->new Learner(u.getUserId(),((u.getFirstName()==null?"":u.getFirstName())+" "+(u.getLastName()==null?"":u.getLastName())).trim(),u.getEmail())).toList();}
 @GetMapping("/staff-directory") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
 public List<Member> directory(){return users.staffDirectory().stream().map(u->new Member(u.getUserId(),((u.getFirstName()==null?"":u.getFirstName())+" "+(u.getLastName()==null?"":u.getLastName())).trim(),u.getRole())).toList();}
 @GetMapping("/staff/learners") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
 public List<Learner> learners(@RequestParam List<String> ids){if(ids.isEmpty()||ids.size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tối đa 100 học viên mỗi lần");return users.findAllById(ids).stream().filter(u->"USER".equals(u.getRoleName())).map(u->new Learner(u.getUserId(),((u.getFirstName()==null?"":u.getFirstName())+" "+(u.getLastName()==null?"":u.getLastName())).trim(),u.getEmail())).toList();}
 @GetMapping("/admin/audit") @PreAuthorize("hasRole('ADMIN')")
 public PageView<Entry> history(@RequestParam(defaultValue="0") int page){
  if(page<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trang không hợp lệ");
  var rows=audit.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page,20)).map(a->new Entry(a.getId(),a.getActorUserId(),a.getAction(),a.getTargetUserId(),(a.getOldValue()==null?"":a.getOldValue())+" → "+a.getNewValue(),a.getCreatedAt()));
  return new PageView<>(rows.getContent(),rows.getTotalElements(),rows.getNumber(),rows.getTotalPages());
 }
}
