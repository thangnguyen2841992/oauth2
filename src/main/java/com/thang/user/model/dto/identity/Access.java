package com.thang.user.model.dto.identity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Access {
    String manageGroupMembership;
    String view;
    String mapRoles;
    String impersonate;
    String manage;

}
