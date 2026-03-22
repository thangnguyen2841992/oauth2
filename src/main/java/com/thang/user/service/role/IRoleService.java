package com.thang.user.service.role;

import com.thang.user.model.Role;

import java.util.List;

public interface IRoleService {
    void addNewRole(Role role);

    List<Role> getAllRoles();

    Role findRoleByRoleName(String roleName);

}
