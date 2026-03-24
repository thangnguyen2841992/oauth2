package com.thang.user.model.dto.identity;

import lombok.*;

@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@Builder
public class Credentials {
    String type;

    String value;

    boolean temporary;
}
