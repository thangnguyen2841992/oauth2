package com.thang.user.model.dto.identity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetRoleIdResponse {
    String id;

    String name;
}
